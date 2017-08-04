package cn.wsgwz.fuguemini;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.wsgwz.fuguemini.core.LocalVpnService;


public class MainActivity extends Activity implements Switch.OnCheckedChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener{

    private static final String TAG = MainActivity.class.getSimpleName();



    private boolean havePermisson = true;
    private boolean textChange;

    private Switch sS;
    private EditText eC;

    private SharedPreferences prefs;




    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        Log.d(TAG,"onCheckedChanged b="+b);
        if(b){
            if(textChange){
                String cStr = eC.getText().toString();
                prefs.edit().putString("config",cStr).apply();
                textChange = false;
            }

        }

        if(havePermisson){
            prefs.edit().putBoolean("sS",b).apply();
            Intent intent = VpnService.prepare(this);
            if (intent != null) {
                startActivityForResult(intent, 1);
            } else {
                onActivityResult(1, RESULT_OK, null);
            }
        }else{
            havePermisson = true;
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==1) {
            if(resultCode == RESULT_OK){
                havePermisson = true;
                Intent intent = new Intent(this, LocalVpnService.class);
                if(sS.isChecked()){
                    intent.setAction("start");
                    intent.putExtra("config",eC.getText().toString());
                }else {
                    intent.setAction("stop");
                }
                startService(intent);
            }else {
                havePermisson = false;
               prefs.edit().putBoolean("sS",false).apply();
            }

        }else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Log.d(TAG,s+"");
        switch (s){
            case "sS":
                boolean s1 = sharedPreferences.getBoolean("sS",false);
                if(s1!=sS.isChecked()) sS.setChecked(s1);
                break;
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        RelativeLayout titleRL = new RelativeLayout(this);
        sS = new Switch(this);

        RelativeLayout.LayoutParams sSParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        sSParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

        eC = new EditText(this);
        eC.setGravity(Gravity.START);
        eC.setTextSize(TypedValue.COMPLEX_UNIT_SP,10);
        eC.setMinLines(8);
        eC.setMaxLines(28);
        eC.setMinEms(28);
        eC.setHint("请输入模式");





        titleRL.addView(eC);
        titleRL.addView(sS,sSParams);

        setContentView(titleRL);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        String cStr = prefs.getString("config",null);
        if(cStr!=null&&!cStr.trim().equals("")){
            setColor(cStr);
        }

        sS.setOnCheckedChangeListener(this);
        sS.setChecked(prefs.getBoolean("sS",false));

        eC.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                Log.d(TAG,"afterTextChanged");
                textChange = true;
            }
        });



    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setColor(String str) {

        try {


            if (!(str.contains("{") && str.contains("}"))) {
                eC.setTextColor(Color.parseColor("#E6E6E6"));
                eC.setText(str);
                return;
            }


            int x0 = eC.getSelectionStart();
            int y0 = eC.getSelectionEnd();
            SpannableString s = new SpannableString(str);


            if (true) {
                Pattern p = Pattern.compile("(\"(version|apn|dns|http|https|support|direct|dispose|proxy|delete|first|connect)\"\\s*:)");
                Matcher m = p.matcher(s);
                while (m.find()) {
                    int start = m.start();
                    int end = m.end();
                    s.setSpan(new ForegroundColorSpan(Color.parseColor("#ECB866")), start + 1, end - 2,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            if (true) {
                Pattern p = Pattern.compile("\"");
                Matcher m = p.matcher(s);
                while (m.find()) {
                    int start = m.start();
                    int end = m.end();
                    s.setSpan(new ForegroundColorSpan(Color.parseColor("#9876AA")), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            if (true) {
                Pattern p = Pattern.compile("\\}|\\{");
                Matcher m = p.matcher(s);
                while (m.find()) {
                    int start = m.start();
                    int end = m.end();
                    s.setSpan(new ForegroundColorSpan(Color.parseColor("#A3B1C0")), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }


            if (true) {
                Pattern p = Pattern.compile("(\"\\s*:\\s*\"*)([\\s\\S]*?)(\"|(,(\\s*)\") )");
                Matcher m = p.matcher(s);
                while (m.find()) {
                    int start = m.start(2);
                    int end = m.end(2);
                    String s2 = m.group(2);
                    if (s2 != null && s2.contains(",") && (s2.startsWith("true") || s2.startsWith("false"))) {
                        String z = s2.substring(0, s2.indexOf(","));
                        end = start + z.length();
                    }
                    //Log.d(TAG,"-->"+m.group(3)+"<");
                    s.setSpan(new ForegroundColorSpan(Color.parseColor("#A5B4C3")), start, end,//#CC7832
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }


            if (true) {
                Pattern p = Pattern.compile("(\\[(?i)m\\])|(\\[(?i)method\\])  |" +
                        "(\\[(?i)u\\])|(\\[(?i)uri\\])|" +
                        "(\\[(?i)url\\])|" +
                        "(\\[(?i)v\\])|(\\[(?i)version\\])|" +
                        "(\\[(?i)h\\])|(\\[(?i)host\\])|" +


                        "(\\[MTD\\])|" +
                        "(\\[Nn\\])|" +
                        "(\\[Rr\\])|" +
                        "(\\[Tt\\])|" +


                        "(\\[(?i)host_no_port\\])|" +
                        "(\\[(?i)port\\])");
                Matcher m = p.matcher(s);
                Log.d(TAG, "+" + s);
                while (m.find()) {
                    int start = m.start();
                    int end = m.end();
                    //Log.d(TAG, "+" + start + m.group());
                    s.setSpan(
                            //  new UnderlineSpan()//设置下划线
                            //new SubscriptSpan()//设置上下标
                            //new ForegroundColorSpan(Color.parseColor("#ff0000"))

                            new StyleSpan(android.graphics.Typeface.BOLD_ITALIC)
                            , start, end,//#CC7832
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }


            eC.setText(s);
            eC.setSelection(x0, y0);


        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();
        }

    }

}
