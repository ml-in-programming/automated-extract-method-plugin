package apeman_core.prediction

import apeman_core.base_entities.FeatureType
import apeman_core.base_entities.CandidateWithFeatures
import apeman_core.base_entities.CandidatesWithFeaturesAndProba
import com.intellij.util.io.isDirectory

import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import org.tensorflow.Tensors
import org.tensorflow.framework.MetaGraphDef
import plugin
import java.io.File
import java.nio.file.Paths

val SAVED_MODEL_DIR = plugin().path.resolve("model_tf_base").absolutePath

class TensorFlowModelProvider(
        private val candidates: List<CandidateWithFeatures>
) {
    fun predictCandidates(): List<CandidatesWithFeaturesAndProba> {

        val last_model_path = getLastModelPath().absolutePath
        val input = getInputList()

        SavedModelBundle.load(last_model_path, "serve").use { model ->
//            printSignature(model)
            val proba = getProba(model, input)
            return candidatesAndProbaZipped(proba)
        }
    }

    private fun printSignature(model: SavedModelBundle) {
        val m = MetaGraphDef.parseFrom(model.metaGraphDef())
        val sig = m.getSignatureDefOrThrow("predict")
        val numInputs = sig.inputsCount
        var i = 1
        println("MODEL SIGNATURE")
        println("Inputs:")
        for (entry in sig.inputsMap) {
            val t = entry.value
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numInputs, entry.key, t.name, t.dtype)
        }
        val numOutputs = sig.outputsCount
        i = 1
        println("Outputs:")
        for (entry in sig.outputsMap) {
            val t = entry.value
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numOutputs, entry.key, t.name, t.dtype)
        }
        println("-----------------------------------------------")
    }

    private fun getLastModelPath(): File {
        val base_dir = Paths.get(SAVED_MODEL_DIR)
        assert(base_dir.isDirectory())

        val last_model = base_dir
                .toFile()
                .listFiles()
                .maxBy { it.name }

        return last_model!!
    }

    private fun getInputList() = FeatureType
            .values()
            .filter { candidates[0].features[it]!! >= 0 && it.name != "NUM_ASSERT" }
            .filterNot {
                setOf(
                        "NAME_CANDIDATE",
                        "NUM_LITERAL",
                        "CON_LITERAL",
                        "CON_ASSERT",
                        "VAR_ACCESS_COUPLING",
                        "VAR_ACCESS_COHESION_2",
                        "TYPE_ACCESS_COUPLING_2",
                        "TYPE_ACCESS_COUPLING",
                        "PACKAGE_COHESION_2",
                        "NUM_SWITCH",
                        "NUM_CONDITIONAL",
                        "INVOCATION_COUPLING",
                        "FIELD_ACCESS_COUPLING_2",
                        "FIELD_ACCESS_COUPLING",
                        "FIELD_ACCESS_COHESION",
                        "VAR_ACCESS_COUPLING_2",
                        "VAR_ACCESS_COHESION",
                        "TYPED_ELEMENTS_COUPLING",
                        "PACKAGE_COUPLING_2",
                        "NUM_LOCAL",
                        "NUM_INVOCATION",
                        "NUM_IF",
                        "NUM_FIELD_ACCESS",
                        "NUM_ASSIGN",
                        "INVOCATION_COHESION",
                        "FIELD_ACCESS_COHESION_2",
                        "CON_SWITCH",
                        "CON_CONDITIONAL",
                        "TYPE_ACCESS_COHESION_2",
                        "TYPE_ACCESS_COHESION",
                        "TYPED_ELEMENTS_COHESION",
                        "PACKAGE_COUPLING",
                        "NUM_LOC",
                        "NUM_COMMENTS",
                        "LOC_RATIO",
                        "CON_IF"

//                    "NAME_CANDIDATE",
//                    "NUM_LITERAL",
//                    "CON_LITERAL",
//                    "CON_ASSERT",
//                    // "CON_SWITCH",
//                    // "CON_INVOCATION",
//                    // "CON_CONDITIONAL",
//                    "CON_TYPE_ACCESS",
//                    "CON_VAR_ACCESS",
//                    "CON_PACKAGE",
//                    "CON_TYPED_ELEMENTS",
//                    "CON_FIELD_ACCESS",
//                    "CON_ASSIGN",
//                    // "NUM_SWITCH",
//                    // "NUM_CONDITIONAL",
//                    "NUM_PACKAGE",
//                    "NUM_COMMENTS",
//                    "NUM_IF",
//                    "NUM_LOCAL",
//                    "NUM_LOC",
//                    "NUM_TYPED_ELEMENTS",
//                    // "NUM_TYPE_ACCESS",
//                    "NUM_INVOCATION",
//                    "NUM_ASSIGN",
//                    "LOC_RATIO",
//                    "CON_IF",
//                    // "FIELD_ACCESS_COUPLING",
//                    // "FIELD_ACCESS_COHESION",
//                    // "FIELD_ACCESS_COUPLING_2",
//                    "FIELD_ACCESS_COHESION_2",
//                    "VAR_ACCESS_COUPLING",
//                    "VAR_ACCESS_COUPLING_2",
//                    "VAR_ACCESS_COHESION",
//                    "VAR_ACCESS_COHESION_2",
//                    // "TYPE_ACCESS_COUPLING",
//                    "TYPE_ACCESS_COUPLING_2",
//                    "TYPE_ACCESS_COHESION",
//                    "TYPE_ACCESS_COHESION_2",
//                    "TYPED_ELEMENTS_COUPLING",
//                    "TYPED_ELEMENTS_COHESION",
//                    // "PACKAGE_COUPLING",
//                    "PACKAGE_COUPLING_2",
//                    "PACKAGE_COHESION",
//                    "PACKAGE_COHESION_2",
//                    // "INVOCATION_COUPLING",
//                    "INVOCATION_COHESION",
//                    "MEAN_NESTING_DEPTH",
//                    "METHOD_MEAN_NESTING_DEPTH"
                ).contains(it.name)
            }
            .map { featureToTensor(it) }

    private fun featureToTensor(featureName: FeatureType) = featureName.name to Tensors.create(
            candidates.map { (_, features) ->
                features[featureName]!!
            }.toDoubleArray())!!

    private fun getProba(
            model: SavedModelBundle,
            input: List<Pair<String, Tensor<Double>>>
    ): List<Float> {

        var runner = model.session().runner()

        input.forEach { (featureName, tensor) ->
            runner = runner.feed(featureName, tensor)
        }

        val output = runner.fetch("boosted_trees/BoostedTreesPredict").run()
        assert(output.count() == 1)
        val probaTensor = output[0].expect(Float::class.javaObjectType)!!

        val array2dOfFloat = Array(candidates.count()) { FloatArray(1) }
        val proba = probaTensor.copyTo(array2dOfFloat)
        return proba.map { it[0] }
    }

    private fun candidatesAndProbaZipped(proba: List<Float>) =
            candidates.zip(proba)
            .map { (candFeat, prob) ->
                CandidatesWithFeaturesAndProba(
                        candFeat.candidate,
                        candFeat.features,
                        prob.toDouble()
                )
            }
}
