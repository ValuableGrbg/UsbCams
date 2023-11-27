package com.example.usbcamtry


import android.content.Context
import android.graphics.*
import android.hardware.usb.UsbDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.example.usbcamtry.databinding.FragmentDemoBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.MultiCameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.utils.ToastUtils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.experimental.and


//TODO Permission writing to storage

/** Multi-road camera demo
 *
 * @author Created by jiangdg on 2022/7/20
 */
class DemoMultiCameraFragment : MultiCameraFragment(), ICameraStateCallBack {
    var isImg = false;
    private lateinit var mAdapter: CameraAdapter
    private lateinit var mViewBinding: FragmentDemoBinding
    private val mCameraList by lazy {
        ArrayList<MultiCameraClient.ICamera>()
    }
    private val mHasRequestPermissionList by lazy {
        ArrayList<MultiCameraClient.ICamera>()
    }
    private var mCurrentCameraPosition = 0

    override fun onCameraAttached(camera: MultiCameraClient.ICamera) {
        mAdapter.data.add(camera)
        mAdapter.notifyItemInserted(mAdapter.data.size - 1)
        mViewBinding.multiCameraTip.visibility = View.GONE
    }

    override fun onCameraDetached(camera: MultiCameraClient.ICamera) {
        mHasRequestPermissionList.remove(camera)
        for ((position, cam) in mAdapter.data.withIndex()) {
            if (cam.getUsbDevice().deviceId == camera.getUsbDevice().deviceId) {
                camera.closeCamera()
                mAdapter.data.removeAt(position)
                mAdapter.notifyItemRemoved(position)
                break
            }
        }
        if (mAdapter.data.isEmpty()) {
            mViewBinding.multiCameraTip.visibility = View.VISIBLE
        }
    }

    override fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }

    override fun onCameraConnected(camera: MultiCameraClient.ICamera) {
        /*for ((position, cam) in mAdapter.data.withIndex()) {
            if (cam.getUsbDevice().deviceId == camera.getUsbDevice().deviceId) {
                val textureView = mAdapter.getViewByPosition(position, R.id.multi_camera_texture_view)
                cam.openCamera(textureView, getCameraRequest())
                cam.setCameraStateCallBack(this)
                break
            }
        }*/
        // request permission for other camera

        val camer = mAdapter.data[mCurrentCameraPosition]
        val fullScreenTextureView = mViewBinding.multiCameraFullScreenTextureView
        camer.openCamera(null, getCameraRequest()) /*TODO*/
        camer.setCameraStateCallBack(this)
        mAdapter.data.forEach { cam ->
            val device = cam.getUsbDevice()
            if (! hasPermission(device)) {
                mHasRequestPermissionList.add(cam)
                requestPermission(device)
                return@forEach
            }
        }
    }

    override fun onCameraDisConnected(camera: MultiCameraClient.ICamera) {
        camera.closeCamera()
    }


    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        if (code == ICameraStateCallBack.State.ERROR) {
            ToastUtils.show(msg ?: "open camera failed.")
        }
        for ((position, cam) in mAdapter.data.withIndex()) {
            if (cam.getUsbDevice().deviceId == self.getUsbDevice().deviceId) {
                mAdapter.notifyItemChanged(position, "switch")
                break
            }
        }
    }

    private fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        var bitmap: Bitmap? = null
        try {
            val image = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val stream = ByteArrayOutputStream()
            image.compressToJpeg(Rect(0, 0, width, height), 20, stream)
            bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bitmap
    }

    fun bitmapFromRgba(width: Int, height: Int, bytes: ByteArray): Bitmap? {
        val pixels = IntArray(bytes.size / 4)
        var j = 0
        for (i in pixels.indices) {
            val R: Int = (bytes[j++] and 0xff.toByte()).toInt()
            val G: Int = (bytes[j++] and 0xff.toByte()).toInt()
            val B: Int = (bytes[j++] and 0xff.toByte()).toInt()
            val A: Int = (bytes[j++] and 0xff.toByte()).toInt()
            val pixel = A shl 24 or (R shl 16) or (G shl 8) or B
            pixels[i] = pixel
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    override fun initView() {
        super.initView()
        openDebug(true)
        mAdapter = CameraAdapter()
        mAdapter.setNewData(mCameraList)

        //mAdapter.bindToRecyclerView(mViewBinding.multiCameraRv)
        //mViewBinding.multiCameraRv.adapter = mAdapter
        //mViewBinding.multiCameraRv.layoutManager = GridLayoutManager(requireContext(), 2)
        mViewBinding.multiCameraFullBtn.setOnClickListener {
            //mViewBinding.textView.text = mCurrentCameraPosition.toString()+"  "+mAdapter.data.size.toString()
            if (mAdapter.data.isNotEmpty()) {
                var camera = mAdapter.data[mCurrentCameraPosition]
                camera.closeCamera()
                mCurrentCameraPosition = (mCurrentCameraPosition + 1) % (mAdapter.data.size)
                camera = mAdapter.data[mCurrentCameraPosition]
                val fullScreenTextureView = mViewBinding.multiCameraFullScreenTextureView
                camera.addPreviewDataCallBack(object: IPreviewDataCallBack{
                    override fun onPreviewData(
                        data: ByteArray?,
                        width: Int,
                        height: Int,
                        format: IPreviewDataCallBack.DataFormat
                    ) {
                        if (data != null && !isImg) {
                            isImg = true
                            mViewBinding.textView.text = format.toString()
                            var mat: Mat
                            //val b: Bitmap?
                            //val b = BitmapFactory.decodeByteArray(data, 0, data.size)
                            val b = nv21ToBitmap(data, width, height)
                            mViewBinding.imageView.setImageBitmap(b)
                            mViewBinding.textView.text = "img showed"
                        }/*else{
                            mViewBinding.textView.text = "NO DATa"
                        }*/
                    }
                })
                camera.openCamera(null, getCameraRequest())/*TODO*/
                camera.setCameraStateCallBack(this)
                ToastUtils.show("onPreviewData")

            }
        }

        /*
        mAdapter.bindToRecyclerView(mViewBinding.multiCameraRv)
        mViewBinding.multiCameraRv.adapter = mAdapter
        mViewBinding.multiCameraRv.layoutManager = GridLayoutManager(requireContext(), 2)
        val mLayoutManager = GridLayoutManager(requireContext(), 2)
        mViewBinding.multiCameraRv.layoutManager = mLayoutManager
        mAdapter.setOnItemChildClickListener { adapter, view, position ->
            val camera = adapter.data[position] as MultiCameraClient.ICamera
            when (view.id) {
                R.id.multi_camera_capture_image -> {
                    camera.captureImage(object : ICaptureCallBack {
                        override fun onBegin() {}

                        override fun onError(error: String?) {
                            ToastUtils.show(error ?: "capture image failed")
                        }

                        override fun onComplete(path: String?) {
                            ToastUtils.show(path ?: "capture image success")
                        }
                    })
                }
                R.id.multi_camera_capture_video -> {
                    if (camera.isRecording()) {
                        camera.captureVideoStop()
                        return@setOnItemChildClickListener
                    }
                    camera.captureVideoStart(object : ICaptureCallBack {
                        override fun onBegin() {
                            mAdapter.notifyItemChanged(position, "video")
                        }

                        override fun onError(error: String?) {
                            mAdapter.notifyItemChanged(position, "video")
                            ToastUtils.show(error ?: "capture video failed")
                        }

                        override fun onComplete(path: String?) {
                            mAdapter.notifyItemChanged(position, "video")
                            ToastUtils.show(path ?: "capture video success")
                        }
                    })
                }
                else -> {
                }
            }
        }*/
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    private fun getCameraRequest(): CameraRequest {
        //val height = mViewBinding.root.height
        //val width = mViewBinding.root.width
        val height = 480
        val width = 640
        return CameraRequest.Builder()
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setPreviewWidth(width)
            .setPreviewHeight(height)
            .setRawPreviewData(true)
            .create()
    }

    inner class CameraAdapter :
        BaseQuickAdapter<MultiCameraClient.ICamera, BaseViewHolder>(R.layout.layout_item_camera) {
        override fun convert(helper: BaseViewHolder, camera: MultiCameraClient.ICamera?) {}

        override fun convertPayloads(
            helper: BaseViewHolder,
            camera: MultiCameraClient.ICamera?,
            payloads: MutableList<Any>
        ) {
            camera ?: return
            if (payloads.isEmpty()) {
                return
            }
            helper.setText(R.id.multi_camera_name, camera.getUsbDevice().deviceName)
            helper.addOnClickListener(R.id.multi_camera_capture_video)
            helper.addOnClickListener(R.id.multi_camera_capture_image)
            // local update
            val switchIv = helper.getView<ImageView>(R.id.multi_camera_switch)
            val captureVideoIv = helper.getView<ImageView>(R.id.multi_camera_capture_video)
            if (payloads.find { "switch" == it } != null) {
                if (camera.isCameraOpened()) {
                    switchIv.setImageResource(R.mipmap.ic_switch_on)
                } else {
                    switchIv.setImageResource(R.mipmap.ic_switch_off)
                }
            }
            if (payloads.find { "video" == it } != null) {
                if (camera.isRecording()) {
                    captureVideoIv.setImageResource(R.mipmap.ic_capture_video_on)
                } else {
                    captureVideoIv.setImageResource(R.mipmap.ic_capture_video_off)
                }
            }
        }
    }
}
