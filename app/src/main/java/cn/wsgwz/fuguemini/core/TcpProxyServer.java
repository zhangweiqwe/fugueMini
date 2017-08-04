package cn.wsgwz.fuguemini.core;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import cn.wsgwz.fuguemini.tunnel.Tunnel;

public class TcpProxyServer implements Runnable {

    private static final String TAG = TcpProxyServer.class.getSimpleName();



    public boolean Stopped;
    public short Port;

    Selector m_Selector;
    ServerSocketChannel m_ServerSocketChannel;
    Thread m_ServerThread;


    public TcpProxyServer(int port) throws IOException {
        m_Selector = Selector.open();
        m_ServerSocketChannel = ServerSocketChannel.open();
        m_ServerSocketChannel.configureBlocking(false);
        m_ServerSocketChannel.socket().bind(new InetSocketAddress(port));
        m_ServerSocketChannel.register(m_Selector, SelectionKey.OP_ACCEPT);
        this.Port = (short) m_ServerSocketChannel.socket().getLocalPort();
        Log.d(Constant.TAG, "AsyncTcpServer listen on " + (this.Port & 0xFFFF));
    }

    public synchronized void start() {
        m_ServerThread = new Thread(this);
        m_ServerThread.setName("TcpProxyServerThread");
        m_ServerThread.start();
    }

    public synchronized void stop() {
        this.Stopped = true;
        if (m_Selector != null) {
            try {
                m_Selector.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                m_Selector = null;
            }
        }

        if (m_ServerSocketChannel != null) {
            try {
                m_ServerSocketChannel.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                m_ServerSocketChannel = null;
            }
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                m_Selector.select();
                Iterator<SelectionKey> keyIterator = m_Selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        try {
                            //Log.d(TAG,key.isReadable()+" "+key.isWritable()+" "+key.isConnectable()+" "+key.isAcceptable());

                            if (key.isReadable()) {// 判断是否有数据发送过来
                                // 从客户端读取数据
                                ((Tunnel) key.attachment()).onReadable(key);
                            } else if (key.isConnectable()) {
                                ((Tunnel) key.attachment()).onConnectable();
                            } else if (key.isAcceptable()) {
                                // 有客户端连接请求时
                                onAccepted(key);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    keyIterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.stop();
        }
    }

    private class InetSocketAddressAndIsSSL{
        private InetSocketAddress inetSocketAddress;
        private boolean isSSL;

        public InetSocketAddressAndIsSSL(InetSocketAddress inetSocketAddress, boolean isSSL) {
            this.inetSocketAddress = inetSocketAddress;
            this.isSSL = isSSL;
        }
    }

    private InetSocketAddressAndIsSSL getDestAddress(SocketChannel localChannel) {
        short portKey = (short) localChannel.socket().getPort();
        NatSession session = NatSessionManager.getSession(portKey);
        if (session != null) {
            return new InetSocketAddressAndIsSSL(new InetSocketAddress(localChannel.socket().getInetAddress(), session.RemotePort & 0xFFFF),session.isSSL);
        }
        return null;
    }

    void onAccepted(SelectionKey key) {
        Tunnel localTunnel = null;
        try {
            SocketChannel localChannel = m_ServerSocketChannel.accept();
            localTunnel = TunnelFactory.wrap(localChannel, m_Selector);


            InetSocketAddressAndIsSSL inetSocketAddressAndIsSSL = getDestAddress(localChannel);

            if (inetSocketAddressAndIsSSL != null) {
                Tunnel remoteTunnel = TunnelFactory.createTunnelByConfig(inetSocketAddressAndIsSSL.isSSL,inetSocketAddressAndIsSSL.inetSocketAddress, m_Selector);
                remoteTunnel.setBrotherTunnel(localTunnel);
                localTunnel.setBrotherTunnel(remoteTunnel);
                remoteTunnel.connect(inetSocketAddressAndIsSSL.inetSocketAddress);
            } else {
               /* LocalVpnService.Instance.writeLog("Error: socket(%s:%d) target host is null.",
                        localChannel.socket().getInetAddress().toString(), localChannel.socket().getPort());*/
                localTunnel.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
            //LocalVpnService.Instance.writeLog("Error: remote socket create failed: %s", e.toString());
            if (localTunnel != null) {
                localTunnel.dispose();
            }
        }
    }

}
