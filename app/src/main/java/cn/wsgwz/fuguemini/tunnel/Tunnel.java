package cn.wsgwz.fuguemini.tunnel;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cn.wsgwz.fuguemini.core.Constant;
import cn.wsgwz.fuguemini.core.LocalVpnService;

public abstract class Tunnel {

    private static final String TAG = Tunnel.class.getSimpleName();



    public boolean isLocalTunnel;
    public boolean isRemoteTunnel;

    protected InetSocketAddress m_DestAddress;
    private SocketChannel m_InnerChannel;
    private ByteBuffer m_SendRemainBuffer;
    private Selector m_Selector;
    private Tunnel m_BrotherTunnel;
    private boolean m_Disposed;
    protected InetSocketAddress m_ServerEP;
    public Tunnel(SocketChannel innerChannel, Selector selector) {
        this.m_InnerChannel = innerChannel;
        this.m_Selector = selector;
    }
    public Tunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
        SocketChannel innerChannel = SocketChannel.open();
        innerChannel.configureBlocking(false);
        this.m_InnerChannel = innerChannel;
        this.m_Selector = selector;
        this.m_ServerEP = serverAddress;
    }

    protected abstract void onConnected(ByteBuffer buffer) throws Exception;

    protected abstract boolean isTunnelEstablished();


    protected abstract void beforeSend(ByteBuffer buffer,int len) throws Exception;

    protected abstract void afterReceived(ByteBuffer buffer) throws Exception;

    protected abstract void onDispose();

    public void setBrotherTunnel(Tunnel brotherTunnel) {
        m_BrotherTunnel = brotherTunnel;
    }

    public void connect(InetSocketAddress destAddress) throws Exception {
        //Log.d(TAG,destAddress.getHostName()+":"+destAddress.getPort());
        //联网关键
        if (LocalVpnService.Instance.protect(m_InnerChannel.socket())) {
            m_DestAddress = destAddress;
            m_InnerChannel.register(m_Selector, SelectionKey.OP_CONNECT, this);
            m_InnerChannel.connect(m_ServerEP);
        } else {
            throw new Exception("VPN protect socket failed.");
        }
    }

    protected void beginReceive() throws Exception {
        if (m_InnerChannel.isBlocking()) {
            m_InnerChannel.configureBlocking(false);
        }
        m_InnerChannel.register(m_Selector, SelectionKey.OP_READ, this);
    }


    protected boolean write(ByteBuffer buffer, boolean copyRemainData) throws Exception {
        int bytesSent;
        while (buffer.hasRemaining()) {
            bytesSent = m_InnerChannel.write(buffer);
            if (bytesSent == 0) {
                break;
            }
        }

        if (buffer.hasRemaining()) {
            if (copyRemainData) {
                if (m_SendRemainBuffer == null) {
                    m_SendRemainBuffer = ByteBuffer.allocate(buffer.capacity());
                }
                m_SendRemainBuffer.clear();
                m_SendRemainBuffer.put(buffer);
                m_SendRemainBuffer.flip();
                m_InnerChannel.register(m_Selector, SelectionKey.OP_WRITE, this);
            }
            return false;
        } else {
            return true;
        }
    }

    protected void onTunnelEstablished() throws Exception {
        this.beginReceive();
        m_BrotherTunnel.beginReceive();
    }

    @SuppressLint("DefaultLocale")
    public void onConnectable() {
        try {
            if (m_InnerChannel.finishConnect()) {
                onConnected(ByteBuffer.allocate(2048));
            } else {
                this.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.dispose();
        }
    }

    public void onReadable(SelectionKey key) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            buffer.clear();
            int bytesRead = m_InnerChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                afterReceived(buffer);
                if (isTunnelEstablished() && buffer.hasRemaining()) {
                    m_BrotherTunnel.beforeSend(buffer,bytesRead);
                    if (!m_BrotherTunnel.write(buffer, true)) {
                        key.cancel();
                            Log.d(Constant.TAG, m_ServerEP + "can not read more.");
                    }
                }
            } else if (bytesRead < 0) {
                this.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.dispose();
        }
    }



    public void dispose() {
        disposeInternal(true);
    }

    void disposeInternal(boolean disposeBrother) {
        if (m_Disposed) {
            return;
        } else {
            try {
                m_InnerChannel.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (m_BrotherTunnel != null && disposeBrother) {
                m_BrotherTunnel.disposeInternal(false);
            }

            m_InnerChannel = null;
            m_SendRemainBuffer = null;
            m_Selector = null;
            m_BrotherTunnel = null;
            m_Disposed = true;

            onDispose();
        }
    }
}
