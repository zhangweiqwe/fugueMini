package cn.wsgwz.fuguemini.core;

import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import cn.wsgwz.fuguemini.dns.DnsPacket;
import cn.wsgwz.fuguemini.dns.Question;
import cn.wsgwz.fuguemini.dns.Resource;
import cn.wsgwz.fuguemini.dns.ResourcePointer;
import cn.wsgwz.fuguemini.tcpip.CommonMethods;
import cn.wsgwz.fuguemini.tcpip.IPHeader;
import cn.wsgwz.fuguemini.tcpip.UDPHeader;


public class DnsProxy implements Runnable {

    private static final String TAG = DnsProxy.class.getSimpleName();

    private static final ConcurrentHashMap<Integer, String> IPDomainMaps = new ConcurrentHashMap<Integer, String>();
    private static final ConcurrentHashMap<String, Integer> DomainIPMaps = new ConcurrentHashMap<String, Integer>();
    private final long QUERY_TIMEOUT_NS = 10 * 1000000000L;
    public boolean Stopped;
    private DatagramSocket m_Client;
    private Thread m_ReceivedThread;
    private short m_QueryID;
    private SparseArray<QueryState> m_QueryArray;

    public DnsProxy() throws IOException {
        m_QueryArray = new SparseArray<QueryState>();
        m_Client = new DatagramSocket(0);
    }

    public static String reverseLookup(int ip) {
        return IPDomainMaps.get(ip);
    }

    public synchronized void start() {
        m_ReceivedThread = new Thread(this);
        m_ReceivedThread.setName("DnsProxyThread");
        m_ReceivedThread.start();
    }

    public synchronized void stop() {
        Stopped = true;
        if (m_Client != null) {
            try {
                m_Client.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                m_Client = null;
            }
        }
    }

    @Override
    public void run() {
        try {
            byte[] RECEIVE_BUFFER = new byte[2000];
            IPHeader ipHeader = new IPHeader(RECEIVE_BUFFER, 0);
            ipHeader.Default();
            UDPHeader udpHeader = new UDPHeader(RECEIVE_BUFFER, 20);

            ByteBuffer dnsBuffer = ByteBuffer.wrap(RECEIVE_BUFFER);
            dnsBuffer.position(28);
            dnsBuffer = dnsBuffer.slice();

            DatagramPacket packet = new DatagramPacket(RECEIVE_BUFFER, 28, RECEIVE_BUFFER.length - 28);

            while (m_Client != null && !m_Client.isClosed()) {

                packet.setLength(RECEIVE_BUFFER.length - 28);
                m_Client.receive(packet);

                dnsBuffer.clear();
                dnsBuffer.limit(packet.getLength());
                try {
                    DnsPacket dnsPacket = DnsPacket.FromBytes(dnsBuffer);
                    if (dnsPacket != null) {
                        OnDnsResponseReceived(ipHeader, udpHeader, dnsPacket);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Log.d(Constant.TAG, "DnsResolver Thread Exited.");
            this.stop();
        }
    }

    private int getFirstIP(DnsPacket dnsPacket) {
        for (int i = 0; i < dnsPacket.Header.ResourceCount; i++) {
            Resource resource = dnsPacket.Resources[i];
            if (resource.Type == 1) {
                int ip = CommonMethods.readInt(resource.Data, 0);
                return ip;
            }
        }
        return 0;
    }

    private void tamperDnsResponse(byte[] rawPacket, DnsPacket dnsPacket, int newIP) {
        Question question = dnsPacket.Questions[0];

        dnsPacket.Header.setResourceCount((short) 1);
        dnsPacket.Header.setAResourceCount((short) 0);
        dnsPacket.Header.setEResourceCount((short) 0);

        ResourcePointer rPointer = new ResourcePointer(rawPacket, question.Offset() + question.Length());
        rPointer.setDomain((short) 0xC00C);
        rPointer.setType(question.Type);
        rPointer.setClass(question.Class);
        rPointer.setTTL(10);
        rPointer.setDataLength((short) 4);
        rPointer.setIP(newIP);

        dnsPacket.Size = 12 + question.Length() + 16;
    }


    private void OnDnsResponseReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
        QueryState state = null;
        synchronized (m_QueryArray) {
            state = m_QueryArray.get(dnsPacket.Header.ID);
            if (state != null) {
                m_QueryArray.remove(dnsPacket.Header.ID);
            }
        }

        if (state != null) {
            dnsPacket.Header.setID(state.ClientQueryID);
            ipHeader.setSourceIP(state.RemoteIP);
            ipHeader.setDestinationIP(state.ClientIP);
            ipHeader.setProtocol(IPHeader.UDP);
            ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
            udpHeader.setSourcePort(state.RemotePort);
            udpHeader.setDestinationPort(state.ClientPort);
            udpHeader.setTotalLength(8 + dnsPacket.Size);

            LocalVpnService.Instance.sendUDPPacket(ipHeader, udpHeader);
        }
    }

    private int getIPFromCache(String domain) {
        Integer ip = DomainIPMaps.get(domain);
        if (ip == null) {
            return 0;
        } else {
            return ip;
        }
    }

   /* private boolean interceptDns(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
        Question question = dnsPacket.Questions[0];

        if (ProxyConfig.IS_DEBUG)
            Log.d(Constant.TAG, "DNS Qeury " + question.Domain);

       if (question.Type == 1) {
            if (ProxyConfig.Instance.needProxy(question.Domain)) {
                int fakeIP = getOrCreateFakeIP(question.Domain);
                tamperDnsResponse(ipHeader.m_Data, dnsPacket, fakeIP);

                if (ProxyConfig.IS_DEBUG)
                    Log.d(Constant.TAG, "interceptDns FakeDns: " +
                            question.Domain + " " +
                            CommonMethods.ipIntToString(fakeIP));

                int sourceIP = ipHeader.getSourceIP();
                short sourcePort = udpHeader.getSourcePort();
                ipHeader.setSourceIP(ipHeader.getDestinationIP());
                ipHeader.setDestinationIP(sourceIP);
                ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
                udpHeader.setSourcePort(udpHeader.getDestinationPort());
                udpHeader.setDestinationPort(sourcePort);
                udpHeader.setTotalLength(8 + dnsPacket.Size);
                LocalVpnService.Instance.sendUDPPacket(ipHeader, udpHeader);
                return true;
            }
        }
        return false;
    }*/

    private void clearExpiredQueries() {
        long now = System.nanoTime();
        for (int i = m_QueryArray.size() - 1; i >= 0; i--) {
            QueryState state = m_QueryArray.valueAt(i);
            if ((now - state.QueryNanoTime) > QUERY_TIMEOUT_NS) {
                m_QueryArray.removeAt(i);
            }
        }
    }

    public void onDnsRequestReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {

   /*     Question question = dnsPacket.Questions[0];

        Log.d(TAG,question.Domain+"<"+question.Type);*/
        //if (!interceptDns(ipHeader, udpHeader, dnsPacket)) {
        if (true) {
            QueryState state = new QueryState();
            state.ClientQueryID = dnsPacket.Header.ID;
            state.QueryNanoTime = System.nanoTime();
            state.ClientIP = ipHeader.getSourceIP();
            state.ClientPort = udpHeader.getSourcePort();
            state.RemoteIP = ipHeader.getDestinationIP();
            state.RemotePort = udpHeader.getDestinationPort();

            m_QueryID++;
            dnsPacket.Header.setID(m_QueryID);

            synchronized (m_QueryArray) {
                clearExpiredQueries();
                m_QueryArray.put(m_QueryID, state);
            }

            InetSocketAddress remoteAddress = new InetSocketAddress(CommonMethods.ipIntToInet4Address(state.RemoteIP), state.RemotePort);
            DatagramPacket packet = new DatagramPacket(udpHeader.m_Data, udpHeader.m_Offset + 8, dnsPacket.Size);
            packet.setSocketAddress(remoteAddress);

            try {
                //联网关键
                if (LocalVpnService.Instance.protect(m_Client)) {
                    m_Client.send(packet);
                } else {
                    Log.e(Constant.TAG, "VPN protect udp socket failed.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class QueryState {
        public short ClientQueryID;
        public long QueryNanoTime;
        public int ClientIP;
        public short ClientPort;
        public int RemoteIP;
        public short RemotePort;
    }
}
