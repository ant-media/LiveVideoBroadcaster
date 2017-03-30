package io.antmedia.android.liveVideoBroadcaster;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import io.antmedia.android.broadcaster.utils.Resolution;

/**
 * Created by faraway on 2/3/15.
 */
public class CameraResolutionsFragment extends DialogFragment implements AdapterView.OnItemClickListener {

    private static final String CAMERA_RESOLUTIONS = "CAMERA_RESOLUTIONS";
    private static final String SELECTED_SIZE_WIDTH = "SELECTED_SIZE_WIDTH";
    private static final String SELECTED_SIZE_HEIGHT = "SELECTED_SIZE_HEIGHT";
    private ListView mCameraResolutionsListView;
    private Dialog dialog;
    private CameResolutionsAdapter mresolutionAdapter = new CameResolutionsAdapter();

    private ArrayList<Resolution> mCameraResolutions;
    private int mselectedSizeWidth;
    private int mselectedSizeHeight;


    public void setCameraResolutions(ArrayList<Resolution> cameraResolutions, Resolution selectedSize) {
        this.mCameraResolutions = cameraResolutions;

        this.mselectedSizeWidth = selectedSize.width;
        this.mselectedSizeHeight = selectedSize.height;
        mresolutionAdapter.setCameResolutions(mCameraResolutions);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(CAMERA_RESOLUTIONS, mCameraResolutions);
        outState.putInt(SELECTED_SIZE_WIDTH, mselectedSizeWidth);
        outState.putInt(SELECTED_SIZE_HEIGHT, mselectedSizeHeight);
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(CAMERA_RESOLUTIONS)) {
                this.mCameraResolutions = (ArrayList<Resolution>) savedInstanceState.getSerializable(CAMERA_RESOLUTIONS);
            }

            if (savedInstanceState.containsKey(SELECTED_SIZE_WIDTH) &&
                    savedInstanceState.containsKey(SELECTED_SIZE_WIDTH))
            {
                mselectedSizeWidth = savedInstanceState.getInt(SELECTED_SIZE_WIDTH);
                mselectedSizeHeight = savedInstanceState.getInt(SELECTED_SIZE_HEIGHT);
            }
            mresolutionAdapter.setCameResolutions(mCameraResolutions);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        restoreState(savedInstanceState);
        View v = inflater.inflate(R.layout.layout_camera_resolutions, container, false);

        mCameraResolutionsListView = (ListView) v.findViewById(R.id.camera_resolutions_listview);
        mCameraResolutionsListView.setAdapter(mresolutionAdapter);
        mCameraResolutionsListView.setOnItemClickListener(this);
        mCameraResolutionsListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        dialog = getDialog();
        return v;
    }

    private void setCameraResolution(Resolution size) {
        if (getActivity() instanceof LiveVideoBroadcasterActivity) {
            ((LiveVideoBroadcasterActivity)getActivity()).setResolution(size);
        }
    }



    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Resolution size = mresolutionAdapter.getItem(i);

        setCameraResolution(size);

    }



    class CameResolutionsAdapter extends BaseAdapter {

        ArrayList<Resolution> mcameraResolutions;


        public void setCameResolutions(ArrayList<Resolution> cameraResolutions) {
            this.mcameraResolutions = cameraResolutions;
        }

        @Override
        public int getCount() {
            return mcameraResolutions.size();
        }

        @Override
        public Resolution getItem(int i) {
            //reverse order. Highest resolution is at top
            return mcameraResolutions.get(getCount()-1-i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup viewGroup) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(android.R.layout.simple_list_item_single_choice, null);
                holder = new ViewHolder();
                holder.resolutionText = (TextView) convertView.findViewById(android.R.id.text1);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            //reverse order. Highest resolution is at top
            Resolution size =  getItem(i);

            if (size.width == mselectedSizeWidth &&
                    size.height == mselectedSizeHeight)
            {

                {
                    mCameraResolutionsListView.setItemChecked(i, true);

                }
            }
            String resolutionText = size.width + " x " + size.height;
            // adding auto resolution adding it to the first
            holder.resolutionText.setText(resolutionText);
            return convertView;
        }

        public class ViewHolder {
            public TextView resolutionText;
        }
    }
}
