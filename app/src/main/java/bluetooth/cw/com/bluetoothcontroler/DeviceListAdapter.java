package bluetooth.cw.com.bluetoothcontroler;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.inuker.bluetooth.library.search.SearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Administrator on 2019/1/22.
 */

public class DeviceListAdapter  extends BaseAdapter implements Comparator<SearchResult> {

    private List<SearchResult> mDataList;
    private Context mContext;
    private DeviceItermClickListener mlistener;

    public DeviceListAdapter(Context context){
        mContext = context;
        mDataList = new ArrayList<SearchResult>();
    }

    public void setListener(DeviceItermClickListener listener){
        mlistener = listener;
    }

    public void setDataList(List<SearchResult> datas) {
        mDataList.clear();
        mDataList.addAll(datas);
        Collections.sort(mDataList, this);
        notifyDataSetChanged();
    }


    @Override
    public int getCount() {
        return mDataList.size();
    }

    @Override
    public Object getItem(int position) {
        return mDataList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = LayoutInflater.from(mContext).inflate(R.layout.device_item, parent, false);

        TextView name = ViewHolder.get(convertView, R.id.tv_name);
        TextView address = ViewHolder.get(convertView, R.id.tv_address);
        TextView connState = ViewHolder.get(convertView, R.id.tv_connState);
        TextView rssi = ViewHolder.get(convertView, R.id.tv_rssi);
        TextView dis = ViewHolder.get(convertView, R.id.tv_dis);

        final SearchResult result = (SearchResult) getItem(position);

        String deviceName = result.getName();
        if (deviceName == null || deviceName == "")
            deviceName = "Unknow";

        name.setText(deviceName);
        address.setText(result.getAddress());
        connState.setText("状态：" );
        rssi.setText("Rssi：");

        dis.setText("Dis：");
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mlistener.onDeviceItemClick(position,result);
            }
        });

        return convertView;
    }

    @Override
    public int compare(SearchResult o1, SearchResult o2) {
        return 0;
    }

    public interface DeviceItermClickListener{
        public abstract void onDeviceItemClick(int postion, SearchResult device);
    }



}
