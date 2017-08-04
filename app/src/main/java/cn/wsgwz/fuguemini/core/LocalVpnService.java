package cn.wsgwz.fuguemini.core;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;


import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import cn.wsgwz.fuguemini.Config;
import cn.wsgwz.fuguemini.ConfigJson;
import cn.wsgwz.fuguemini.dns.DnsPacket;
import cn.wsgwz.fuguemini.tcpip.CommonMethods;
import cn.wsgwz.fuguemini.tcpip.IPHeader;
import cn.wsgwz.fuguemini.tcpip.TCPHeader;
import cn.wsgwz.fuguemini.tcpip.UDPHeader;
import cn.wsgwz.fuguemini.MainActivity;

public class LocalVpnService extends VpnService implements Runnable {

    private static final String TAG = LocalVpnService.class.getSimpleName();

    private SharedPreferences prefs;

    public String userAgent = System.getProperty("http.agent");


    public static LocalVpnService Instance;
    private boolean isRunning = false;
    public Config config;


    private int local_ip;

    private Thread m_VPNThread;
    private ParcelFileDescriptor m_VPNInterface;
    private TcpProxyServer m_TcpProxyServer;
    private DnsProxy m_DnsProxy;
    private FileOutputStream m_VPNOutputStream;

    private byte[] m_Packet;
    private IPHeader m_IPHeader;
    private TCPHeader m_TCPHeader;
    private UDPHeader m_UDPHeader;
    private ByteBuffer m_DNSBuffer;



    @Override
    public void onCreate() {
        super.onCreate();

        Instance = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);


        m_Packet = new byte[20000];
        m_IPHeader = new IPHeader(m_Packet, 0);
        m_TCPHeader = new TCPHeader(m_Packet, 20);
        m_UDPHeader = new UDPHeader(m_Packet, 20);
        m_DNSBuffer = ((ByteBuffer) ByteBuffer.wrap(m_Packet).position(28)).slice();




        try {
            m_TcpProxyServer = new TcpProxyServer(0);
            m_TcpProxyServer.start();

            m_DnsProxy = new DnsProxy();
            m_DnsProxy.start();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        switch (intent.getAction()){
            case "start":
                try {
                    String configStr = intent.getStringExtra("config");
                    if(configStr!=null&&!configStr.trim().equals("")){
                        config = ConfigJson.read(new ByteArrayInputStream(configStr.getBytes()));
                    }else {
                        config =  null;
                    }
                } catch (Exception e) {
                    Toast.makeText(this,e.getMessage().toString(),Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
                isRunning = true;
                m_VPNThread = new Thread(this);
                m_VPNThread.start();
                break;
            case "stop":
                isRunning = false;
                break;
        }

        return START_NOT_STICKY;
    }



    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }



    public void sendUDPPacket(IPHeader ipHeader, UDPHeader udpHeader) {
        try {
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
            this.m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    @Override
    public void run() {
        try {
            runVPN();
        } catch (InterruptedException e) {
           e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        dispose();
    }

    private void runVPN() throws Exception {
        this.m_VPNInterface = establishVPN();
        this.m_VPNOutputStream = new FileOutputStream(m_VPNInterface.getFileDescriptor());
        FileInputStream in = new FileInputStream(m_VPNInterface.getFileDescriptor());
        try {
            while (isRunning) {
                boolean idle = true;
                int size = in.read(m_Packet);
                if (size > 0) {
                    if (m_DnsProxy.Stopped || m_TcpProxyServer.Stopped) {
                        in.close();
                        throw new Exception("LocalVpnService stop已经关闭了");
                    }
                    try {
                        onIPPacketReceived(m_IPHeader, size);
                        idle = false;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (idle) {
                    Thread.sleep(100);
                }
            }
        } finally {
            in.close();
        }
    }

  private void onIPPacketReceived(IPHeader ipHeader, int size) throws IOException {
        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
                TCPHeader tcpHeader = m_TCPHeader;
                tcpHeader.m_Offset = ipHeader.getHeaderLength();
                if (ipHeader.getSourceIP() == local_ip) {
                    if (tcpHeader.getSourcePort() == m_TcpProxyServer.Port) {
                        NatSession session = NatSessionManager.getSession(tcpHeader.getDestinationPort());
                        if (session != null) {
                            ipHeader.setSourceIP(ipHeader.getDestinationIP());
                            tcpHeader.setSourcePort(session.RemotePort);
                            ipHeader.setDestinationIP(local_ip);

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);


                            m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                            //m_ReceivedBytes += size;
                        } else {
                                Log.d(Constant.TAG, "NoSession: " +
                                        ipHeader.toString() + " " +
                                        tcpHeader.toString());
                        }
                    } else {
                        int portKey = tcpHeader.getSourcePort();
                        NatSession session = NatSessionManager.getSession(portKey);
                        if (session == null || session.RemoteIP != ipHeader.getDestinationIP() || session.RemotePort != tcpHeader.getDestinationPort()) {
                            session = NatSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader.getDestinationPort());
                        }

                        session.LastNanoTime = System.nanoTime();
                        session.PacketSent++;

                        int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
                        if (session.PacketSent == 2 && tcpDataSize == 0) {
                            return;
                        }

                        if (session.BytesSent == 0 && tcpDataSize > 10) {
                            int dataOffset = tcpHeader.m_Offset + tcpHeader.getHeaderLength();

                            HttpHostHeaderParser.HostAndIsSSL hostAndIsSSL = HttpHostHeaderParser.parseHost(tcpHeader.m_Data, dataOffset, tcpDataSize);
                            if (hostAndIsSSL != null) {
                                session.RemoteHost = hostAndIsSSL.host;
                                session.isSSL = hostAndIsSSL.isSSL;
                            }
                        }




                        ipHeader.setSourceIP(ipHeader.getDestinationIP());
                        ipHeader.setDestinationIP(local_ip);
                        tcpHeader.setDestinationPort(m_TcpProxyServer.Port);//目的地

                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                        m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                        session.BytesSent += tcpDataSize;
                        //m_SentBytes += size;
                    }
                }
                break;
            case IPHeader.UDP:
                UDPHeader udpHeader = m_UDPHeader;
                udpHeader.m_Offset = ipHeader.getHeaderLength();
                if (ipHeader.getSourceIP() == local_ip && udpHeader.getDestinationPort() == 53) {
                    m_DNSBuffer.clear();
                    m_DNSBuffer.limit(ipHeader.getDataLength() - 8);
                    DnsPacket dnsPacket = DnsPacket.FromBytes(m_DNSBuffer);
                    if (dnsPacket != null && dnsPacket.Header.QuestionCount > 0) {
                        m_DnsProxy.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket);
                    }
                }
                break;
        }
    }


    private ParcelFileDescriptor establishVPN() throws Exception {

        NatSessionManager.clearAllSessions();

        Builder builder = new Builder();
        builder.setMtu(1500);

        String ip = "10.0.2.0";
        String[] arrStrings = ip.split("\\.");
        local_ip= (Integer.parseInt(arrStrings[0]) << 24)
                | (Integer.parseInt(arrStrings[1]) << 16)
                | (Integer.parseInt(arrStrings[2]) << 8)
                | Integer.parseInt(arrStrings[3]);
        builder.addAddress(ip, 32);

        builder.addDnsServer("114.114.114.114");
        builder.addRoute("0.0.0.0" ,0);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        builder.setConfigureIntent(pendingIntent);

        builder.setSession("fugue mini");
        ParcelFileDescriptor pfdDescriptor = builder.establish();
        return pfdDescriptor;
    }

    private void dispose() {
        try {
            if (m_VPNInterface != null) {
                m_VPNInterface.close();
                m_VPNInterface = null;
            }
        } catch (Exception e) {
           e.printStackTrace();
        }

        try {
            if (m_VPNOutputStream != null) {
                m_VPNOutputStream.close();
                m_VPNOutputStream = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (m_VPNThread != null) {
            m_VPNThread.interrupt();
            m_VPNThread = null;
        }
    }

    @Override
    public void onDestroy() {
        Instance = null;

        prefs.edit().putBoolean("sS",false).apply();

        try {
            if (m_TcpProxyServer != null) {
                m_TcpProxyServer.stop();
                m_TcpProxyServer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (m_DnsProxy != null) {
                m_DnsProxy.stop();
                m_DnsProxy = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }




}
