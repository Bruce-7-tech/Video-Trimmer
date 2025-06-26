package com.example.videotrimmer.ui.fragments

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.videotrimmer.R
import com.example.videotrimmer.databinding.FragmentVideoTrimmingBinding
import com.example.videotrimmer.ui.extension.addOnBackPressedCallback
import com.example.videotrimmer.ui.extension.showToast
import com.redevrx.video_trimmer.event.OnVideoEditedEvent
import java.io.File
import androidx.core.net.toUri

class VideoTrimmingFragment : Fragment(), OnVideoEditedEvent {
    private var _binding: FragmentVideoTrimmingBinding? = null
    private val binding get() = _binding!!
    private lateinit var progressDialog: ProgressDialog


    @SuppressLint("UnsafeOptInUsageError")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVideoTrimmingBinding.inflate(inflater, container, false)
        addOnBackPressedCallback { findNavController().popBackStack() }

        val path = requireArguments().getString("videoPath")
        progressDialog = ProgressDialog(requireContext())
        progressDialog.setTitle("Crop")
        progressDialog.setCancelable(false)

        val savedFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "cropped-videos"
        )

//        val savedFile = File(requireContext().filesDir, "cropped-videos")

        if (!savedFile.exists()) {
            savedFile.mkdir()
        }

        binding.videoTrimmer.apply {
            setVideoBackgroundColor(resources.getColor(R.color.black))
            setOnTrimVideoListener(this@VideoTrimmingFragment)
            setVideoURI(path!!.toUri())
            setDestinationPath(savedFile.path)
            setVideoInformationVisibility(true)
            setMaxDuration(60)
            setMinDuration(0)
        }

        binding.btnSaveVideo.setOnClickListener {
            binding.videoTrimmer.saveVideo()
            progressDialog.show()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getResult(uri: Uri) {
        progressDialog.dismiss()
        showToast("File cropped and saved in: ${uri.path}")
    }

    override fun onError(message: String) {
        progressDialog.dismiss()
        showToast("Something went wrong: $message")
    }
}