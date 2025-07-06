package com.smoothradio.radio.feature.about.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.smoothradio.radio.R
import com.smoothradio.radio.databinding.AboutFragmentBinding

class AboutFragment : DialogFragment() {

    private var _binding: AboutFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AboutFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInfoText()
        underlineTextViews()
        setupClickListeners()
    }

    private fun setupInfoText() {
        binding.tvInfo.text = getString(R.string.info_text)
    }

    private fun underlineTextViews() = with(binding) {
        fbAddress.paintFlags = Paint.UNDERLINE_TEXT_FLAG
        tvEmail.paintFlags = Paint.UNDERLINE_TEXT_FLAG
    }

    private fun setupClickListeners() = with(binding) {
        fbAddress.setOnClickListener { openFacebookPage() }
        tvEmail.setOnClickListener { sendFeedbackEmail() }
    }

    private fun openFacebookPage() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            getString(R.string.facebook_url).toUri()
        )
        startActivity(intent)
    }

    private fun sendFeedbackEmail() {
        val versionInfo = getAppVersionInfo()
        val deviceName =
            "${getString(R.string.device_name_label)} ${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "${getString(R.string.android_version_label)} ${Build.VERSION.RELEASE}"

        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.email_address)))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
            putExtra(
                Intent.EXTRA_TEXT,
                "$versionInfo\n$deviceName\n$androidVersion\n\n"
            )
        }

        try {
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(Intent.createChooser(intent, getString(R.string.send_mail)))
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                getString(R.string.no_email_client),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getAppVersionInfo(): String {
        return try {
            val pm = requireActivity().packageManager
            val appName = requireActivity().applicationInfo.loadLabel(pm).toString()
            val version = pm.getPackageInfo(requireActivity().packageName, 0).versionName
            "${getString(R.string.app_version_label)} $appName $version"
        } catch (e: PackageManager.NameNotFoundException) {
            "${getString(R.string.app_version_label)} Unknown"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
