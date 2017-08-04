package cn.wsgwz.fuguemini.tunnel;


import android.util.Log;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cn.wsgwz.fuguemini.Config;
import cn.wsgwz.fuguemini.ParamsHelper;
import cn.wsgwz.fuguemini.core.LocalVpnService;

public class RawTunnel extends Tunnel {

    private static final String TAG = RawTunnel.class.getSimpleName();

    public RawTunnel(InetSocketAddress serverAddress, Selector selector) throws Exception {
        super(serverAddress, selector);
        isRemoteTunnel = true;
    }

    public RawTunnel(SocketChannel innerChannel, Selector selector) {
        super(innerChannel, selector);
        isLocalTunnel = true;
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {
        onTunnelEstablished();
    }



    @Override
    protected void beforeSend(ByteBuffer b, int len) throws Exception {
        // TODO Auto-generated method stub

        if(isLocalTunnel){
            return;
        }

        Config config = LocalVpnService.Instance.config;

        if(config==null){
            return;
        }


        byte[] buffer = b.array();

        if ((buffer[0] == 'G' && buffer[1] == 'E') || (buffer[0] == 'P' && buffer[1] == 'O')) {
            if (config.isHttpDispose()) {
                ParamsHelper paramsHelper = ParamsHelper.read(new ByteArrayInputStream(buffer, 0, len), config);
                if (paramsHelper != null) {
                    String request = paramsHelper.toString2();
                    b = ByteBuffer.wrap(request.getBytes());
                }
            }
        } else if(buffer[0] == 'C' && buffer[1] == 'O'){
            if (config.isHttpsDispose()) {
                ParamsHelper paramsHelper = ParamsHelper.read(new ByteArrayInputStream(buffer, 0, len), config);
                String request = paramsHelper.toString2();
                b = ByteBuffer.wrap(request.getBytes());
            }
        }
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        // TODO Auto-generated method stub


    }

    @Override
    protected boolean isTunnelEstablished() {
        return true;
    }

    @Override
    protected void onDispose() {
        // TODO Auto-generated method stub

    }

}
