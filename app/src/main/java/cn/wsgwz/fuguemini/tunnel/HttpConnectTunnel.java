package cn.wsgwz.fuguemini.tunnel;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cn.wsgwz.fuguemini.Config;
import cn.wsgwz.fuguemini.ConnectHelper;
import cn.wsgwz.fuguemini.core.LocalVpnService;


public class HttpConnectTunnel extends Tunnel {


    private static final String TAG = HttpConnectTunnel.class.getSimpleName();

    private boolean m_TunnelEstablished;

    public HttpConnectTunnel(SocketChannel innerChannel, Selector selector) {
        super(innerChannel, selector);
    }

    public HttpConnectTunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
        super(serverAddress, selector);
    }


    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {
        Config config = LocalVpnService.Instance.config;
        String request = ConnectHelper.getConnectRequest(m_DestAddress.getHostName(),
                m_DestAddress.getPort(),LocalVpnService.Instance.userAgent,config);

        buffer.clear();
        buffer.put(request.getBytes());
        buffer.flip();
        if(this.write(buffer,true)){//发送连接请求到代理服务器
            this.beginReceive();//开始接收代理服务器响应数据
        }
    }


    @Override
    protected void beforeSend(ByteBuffer buffer, int len) throws Exception {

    }



    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        if(!m_TunnelEstablished){
            //收到代理服务器响应数据
            //分析响应并判断是否连接成功
            String response=new String(buffer.array(),buffer.position(),12);
            if(response.matches("^HTTP/1.[01] 200$")){
                buffer.limit(buffer.position());
            }else {
                //Log.d(TAG,"--->"+response+m_DestAddress.getPort());
                throw new Exception(String.format("Proxy server responsed an error: %s",response));
            }

            m_TunnelEstablished=true;
            super.onTunnelEstablished();
        }
    }

    @Override
    protected boolean isTunnelEstablished() {
        return m_TunnelEstablished;
    }

    @Override
    protected void onDispose() {
    }


}
