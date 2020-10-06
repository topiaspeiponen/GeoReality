package com.example.georeality

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.navigation.fragment.navArgs
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.gson.Gson
import kotlinx.android.synthetic.main.view_renderable_text.view.*
import java.net.URI


//ArFragment class
class ArFragment : Fragment() {
    private val args : ArFragmentArgs by navArgs()
    private var modelRenderable1 : ModelRenderable? = null
    private var modelRenderable2 : ModelRenderable? = null
    private var viewRenderable : ViewRenderable? = null
    private lateinit var mContext : Context
    private lateinit var arMarkerClass : ARMarker
    private lateinit var fragment : ArFragment

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ar, container, false)
        fragment = childFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment

        //Get the argument in JSON and convert to data class ARMarker
        val arMarkerJson = args.arMarkerJson
        val gson = Gson()
        arMarkerClass = gson.fromJson(arMarkerJson, ARMarker::class.java)

        mContext = requireContext()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragment.arSceneView.scene.addOnUpdateListener(this::onUpdateFrame)
        /**
         * Checking ARMarker class values
         * 1. If 2D or 3D
         * 2. If 3D, check which model to display
         */
        if (arMarkerClass.type == getString(R.string.ar_type_2d)) {
            if (arMarkerClass.text != null) {
                createViewRenderable(arMarkerClass.text!!)
            }
        }
        else if (arMarkerClass.type == getString(R.string.ar_type_3d)) {
            if (arMarkerClass.model_type != null) {
                createModelRenderables(arMarkerClass.model_type!!)
            }

            //Model is duck
            if (arMarkerClass.model_type == getString(R.string.cache_model_duck)) {
                createModelRenderables(getString(R.string.cache_model_duck))
            }
            //Model is avocado
            else if (arMarkerClass.model_type == getString(R.string.cache_model_avocado)) {
                createModelRenderables(getString(R.string.cache_model_avocado))
            }
        }

    }
    private fun onUpdateFrame(frameTime: FrameTime?) {
        //get the frame from the scene for shorthand
        val frame = fragment.arSceneView.arFrame
        if (frame != null) {
            //get the trackables to ensure planes are detected
            val var3 = frame.getUpdatedTrackables(Plane::class.java).iterator()
            if(var3.hasNext()) {
                val plane = var3.next() as Plane

                //If a plane has been detected & is being tracked by ARCore
                if (plane.trackingState == TrackingState.TRACKING) {

                    //Hide the plane discovery helper animation
                    fragment.planeDiscoveryController.hide()


                    //Get all added anchors to the frame
                    val iterableAnchor = frame.updatedAnchors.iterator()

                    //place the first object only if no previous anchors were added
                    if(!iterableAnchor.hasNext()) {
                        //Perform a hit test at the center of the screen to place an object without tapping
                        val hitTest = frame.hitTest(frame.screenCenter().x, frame.screenCenter().y)

                        //iterate through all hits
                        val hitTestIterator = hitTest.iterator()
                        if(hitTestIterator.hasNext() ) {
                            val hitResult = hitTestIterator.next()

                            //Create an anchor at the plane hit
                            val modelAnchor = plane.createAnchor(hitResult.hitPose)

                            //Attach a node to this anchor with the scene as the parent
                            val anchorNode = AnchorNode(modelAnchor)
                            anchorNode.setParent(fragment.arSceneView.scene)

                            //create a new TranformableNode that will carry our object
                            val transformableNode = TransformableNode(fragment.transformationSystem)
                            transformableNode.setParent(anchorNode)
                            if (arMarkerClass.model_type == getString(R.string.cache_model_duck)) {
                                transformableNode.renderable = this@ArFragment.modelRenderable1
                            }
                            else if(arMarkerClass.model_type == getString(R.string.cache_model_avocado)) {
                                transformableNode.renderable = this@ArFragment.modelRenderable2

                            } else if (arMarkerClass.type == getString(R.string.ar_type_2d)){
                                transformableNode.renderable = this@ArFragment.viewRenderable
                            }

                            //Alter the real world position to ensure object renders on the table top. Not somewhere inside.
                            transformableNode.worldPosition = Vector3(modelAnchor.pose.tx(),
                                modelAnchor.pose.compose(Pose.makeTranslation(0f, 0.05f, 0f)).ty(),
                                modelAnchor.pose.tz())
                        }
                    }
                }
            }
        }
    }

    //A method to find the screen center. This is used while placing objects in the scene
    private fun Frame.screenCenter(): Vector3 {
        val vw = view?.findViewById<View>(R.id.sceneform_fragment)
        return Vector3(vw?.width!! / 2f, vw.height / 2f, 0f)
    }

    /**
     * Creates TextView that is displayed in AR mode
     */
    private fun createViewRenderable(displayText : String) {
        val renderableFuture = ViewRenderable.builder()
            .setView(mContext, R.layout.view_renderable_text)
            .build()
        renderableFuture.thenAccept {it ->
            it.view.viewRenderableText.text = displayText
            viewRenderable = it }
    }

    /**
     * Creates models that are displayed in AR mode
     */
    private fun createModelRenderables(modelType : String) {
        val modelMap = createModelMap()

        if (modelType == getString(R.string.cache_model_duck)) {
            val renderableFuture1 = ModelRenderable.builder()
                .setSource(mContext, RenderableSource.builder().setSource(
                        mContext,
                        modelMap[modelType],
                        RenderableSource.SourceType.GLTF2
                    )
                        .setScale(0.5f)
                        .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                        .build()
                )
                .setRegistryId(getString(R.string.cache_model_duck))
                .build()
            renderableFuture1.thenAccept { it -> modelRenderable1 = it }
            renderableFuture1.exceptionally {
                Log.d("Renderable", "Unable to create renderable")
                null
            }
        }
        else if (modelType == getString(R.string.cache_model_avocado)) {
            val renderableFuture2 = ModelRenderable.builder()
                .setSource(mContext, RenderableSource.builder().setSource(
                    mContext,
                    modelMap[modelType],
                    RenderableSource.SourceType.GLTF2
                )
                    .setScale(4f)
                    .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                    .build()
                )
                .setRegistryId(getString(R.string.cache_model_avocado))
                .build()
            renderableFuture2.thenAccept { it -> modelRenderable2 = it }
            renderableFuture2.exceptionally {
                Log.d("Renderable", "Unable to create renderable")
                null
                }
        }
    }

    /**
     * Assigns models urls to their perspective strings in resources
     */
    private fun createModelMap() : Map<String, Uri> {
        val model1 = getString(R.string.cache_model_duck)
        val model2 = getString(R.string.cache_model_avocado)

        val url1 = Uri.parse(getString(R.string.model1_url))
        val url2 = Uri.parse(getString(R.string.model2_url))

        return mapOf(model1 to url1, model2 to url2)
    }
}