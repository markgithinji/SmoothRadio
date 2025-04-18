package com.smoothradio.radio.feature.about;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.smoothradio.radio.R;
import com.smoothradio.radio.databinding.AboutFragmentBinding;

public final class AboutFragment extends Fragment {

    private AboutFragmentBinding binding;

    public AboutFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AboutFragmentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupInfoText();
        underlineTextViews();
        setupClickListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupInfoText() {
        binding.tvInfo.setText(getString(R.string.info_text));
    }

    private void underlineTextViews() {
        binding.fbAddress.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
        binding.tvEmail.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
        binding.tvWatchTv.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
    }

    private void setupClickListeners() {
        binding.tvWatchTv.setOnClickListener(v -> openTvAppOnPlayStore());
        binding.fbAddress.setOnClickListener(v -> openFacebookPage());
        binding.tvEmail.setOnClickListener(v -> sendFeedbackEmail());
    }

    private void openTvAppOnPlayStore() {
        final Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + getString(R.string.tv_app_package)));
        startActivity(intent);
    }

    private void openFacebookPage() {
        final Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.facebook_url)));
        startActivity(intent);
    }

    private void sendFeedbackEmail() {
        final String versionInfo = getAppVersionInfo();
        final String deviceName = getString(R.string.device_name_label) + Build.MANUFACTURER + " " + Build.MODEL;
        final String androidVersion = getString(R.string.android_version_label) + Build.VERSION.RELEASE;

        final Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.email_address)});
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
        intent.putExtra(Intent.EXTRA_TEXT, versionInfo + "\n" + deviceName + "\n" + androidVersion + "\n\n");

        try {
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(Intent.createChooser(intent, getString(R.string.send_mail)));
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), getString(R.string.no_email_client), Toast.LENGTH_SHORT).show();
        }
    }

    private String getAppVersionInfo() {
        try {
            final PackageManager pm = requireActivity().getPackageManager();
            final String appName = requireActivity().getApplicationInfo().loadLabel(pm).toString();
            final String version = pm.getPackageInfo(requireActivity().getPackageName(), 0).versionName;
            return getString(R.string.app_version_label) + appName + " " + version;
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.app_version_label) + "Unknown";
        }
    }
}
