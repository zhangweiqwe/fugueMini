package cn.wsgwz.fuguemini.core;

import android.util.Log;

import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cn.wsgwz.fuguemini.Config;
import cn.wsgwz.fuguemini.tunnel.HttpConnectTunnel;
import cn.wsgwz.fuguemini.tunnel.RawTunnel;
import cn.wsgwz.fuguemini.tunnel.Tunnel;

public class TunnelFactory {

    public static final String TAG = TunnelFactory.class.getSimpleName();
    public static Tunnel wrap(SocketChannel channel, Selector selector) {
        return new RawTunnel(channel, selector);
    }

    public static Tunnel createTunnelByConfig(boolean isSSL, InetSocketAddress destAddress,Selector selector) throws Exception {

        Config config = LocalVpnService.Instance.config;


        if(config==null){
            return new RawTunnel(destAddress, selector);
        }
            if(isSSL){
                return  new HttpConnectTunnel(new InetSocketAddress(config.getHttps_proxy(),config.getHttps_port()),selector);
            }

        return new RawTunnel(new InetSocketAddress(config.getHttp_proxy(),config.getHttp_port()), selector);
    }

}
