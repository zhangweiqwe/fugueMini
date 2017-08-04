package cn.wsgwz.fuguemini;







import android.util.Log;

import java.io.IOException;
import java.io.InputStream;


import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.HashMap;

import cn.wsgwz.fuguemini.core.Constant;

/**
 * Created by Administrator on 2016/11/1.
 */

public class ParamsHelper {
    private static final String TAG = ParamsHelper.class.getSimpleName();

    private String requestType,url,uri,httpVersion,host,url_host;
    public String endOfLine="\r\n";
    private  HashMap<String, String> hashMap = new LinkedHashMap<>();;
    private Config config;
    public String flag0 = "/",flag1 = ": ",flag3 = "",flag4 = ":";
    public int requestTypeInt = 0;

    private InputStream in;

    public  static final ParamsHelper read(InputStream  in ,Config config) throws IOException {
        ParamsHelper paramsHelper = new ParamsHelper();
            paramsHelper.config = config;
            String firstLine = ParamsHelper.readLine(in).toString();
            StringTokenizer tokenizer = new StringTokenizer(firstLine);

        if(tokenizer.countTokens()!=3){
            //SocketD.printf("-->"+firstLine+"<--");
            return null;
        }else {
            //SocketD.printf("-->"+tokenizer.countTokens()+"<--");
        }

            paramsHelper.requestType = tokenizer.nextToken();
            paramsHelper.url = tokenizer.nextToken();

                paramsHelper.httpVersion = tokenizer.nextToken();




            paramsHelper.in = in;

        List<String>  detates = null;
        switch (paramsHelper.requestType){
            case "CONNECT":
                detates = config.getHttps_delate();
                paramsHelper.requestTypeInt = 2;
                break;
            default:
                detates = config.getHttp_delate();
                break;
        }

        boolean needDelates = detates==null?false:true;

        String line = null;
        String key, value = null;
        while (((line = readLine(in).toString()) != null)&&(line.trim().length()!=0)) {
            tokenizer = new StringTokenizer(line);
            key = tokenizer.nextToken(paramsHelper.flag1);
            value = line.replace(key+paramsHelper.flag1, paramsHelper.flag3);
            if(!(needDelates&&detates.contains(key))){
                /*if(key.equals("Range")){

                    *//*if(value.equals("bytes=0-")){
                        paramsHelper.hashMap.put(key, "bytes=0-31457279");
                    }else {
                        paramsHelper.hashMap.put(key, value);
                    }*//*

                    //SocketD.printf("-->"+value+"<---"+paramsHelper.socketD);
                }else {
                    paramsHelper.hashMap.put(key, value);
                }*/
                paramsHelper.hashMap.put(key, value);

            }
            if(key.equals("Host")||key.equals("host")){
                paramsHelper.host = value;
            }


        }
        matchRequestParams(paramsHelper);

        return paramsHelper;
    }

    private static final StringBuilder readLine(InputStream in) throws IOException {
        StringBuilder sb= new StringBuilder();
            int c;
            loop:      while (true){
                switch ((c=in.read())){
                    case -1:
                        break loop;
                    case '\r':
                        int c2 = in.read();
                        if((c2!='\n')&&c2!=-1){
                            sb.append((char)c);
                            sb.append((char)c2);
                            break ;
                        }else {
                            break loop;
                        }
                    default:
                        sb.append((char) c);
                        break ;
                }
            }
        return sb;
    }
    private static final void matchRequestParams(ParamsHelper paramsHelper) {

        if(paramsHelper.requestTypeInt==2){
            return;
        }
        if(paramsHelper.url.startsWith(paramsHelper.flag0)){
            paramsHelper.uri = paramsHelper.url;
        }else {
            paramsHelper.url_host = getUriHost(paramsHelper.url);
            if(paramsHelper.url_host!=null){
                paramsHelper.uri = paramsHelper.url.substring(paramsHelper.url.indexOf(paramsHelper.url_host)+paramsHelper.url_host.length(), paramsHelper.url.length());
            }else {
                paramsHelper.uri = paramsHelper.url;
            }
        }

    }


    //@Override
    public String toString2() throws IOException {
        StringBuilder sb = Match.match(ParamsHelper.this, config);

        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len=in.read(buffer))!=-1){
            sb.append(new String(buffer,0,len));
        }
        if(in!=null){
            in.close();
        }
        //Log.d(Constant.TAG,sb.toString());
        return sb.toString();
    }



    private  static String getUriHost(String url){
        if(url==null){
            return null;
        }
        String host = null;
        Pattern p =  Pattern.compile("(?<=//|)((\\w)+\\.)+\\w+(:\\d*)?");
        Matcher matcher = p.matcher(url);
        if(matcher.find()){
            host = matcher.group();
        }
        return host;
    }


    public String getRequestType() {
        return requestType;
    }
    public String getUrl() {
        return url;
    }
    public String getUri() {
        return uri;
    }
    public String getHttpVersion() {
        return httpVersion;
    }
    public HashMap<String, String> getHashMap() {
        return hashMap;
    }
    public Config getConfig() {
        return config;
    }
    public String getHost() {
        return host;
    }

    public String getUrl_host() {
        return url_host;
    }
}
