package apeman_core

import apeman_core.base_entities.ExtractionCandidate
import apeman_core.candidates_generation.CandidatesOfAnalysisMethods
import apeman_core.candidates_generation.CustomCandidates
import apeman_core.pipes.CandidateWithFeatures
import apeman_core.features_extraction.FeaturesForEveryCandidate
import apeman_core.grouping.GettingBestCandidates
import apeman_core.pipes.CandidatesWithFeaturesAndProba
import apeman_core.prediction.SciKitModelProvider
import apeman_core.prediction.TensorFlowModelProvider
import apeman_core.utils.scopeToTopMethods
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import java.util.logging.Logger

private val log = Logger.getGlobal()

class Launcher(
        analysisScope: AnalysisScope? = null,
        private var analysisMethods: List<PsiMethod>? = null,
        private val analysisCandidates: List<Pair<TextRange, PsiFile>>? = null
) {

    init {
        assert((analysisScope != null) xor (analysisMethods != null) xor (analysisCandidates != null))

        if (analysisScope != null) {
            analysisMethods = scopeToTopMethods(analysisScope)
        }

        assert((analysisMethods != null) xor (analysisCandidates != null))
    }

    fun getCandidatesWithProba(): ArrayList<CandidatesWithFeaturesAndProba> {

        try {
            log.info("generate candidates")
            val candidates = generateCandidates()

            log.info("calculate features")
            val candidatesWithFeatures = calculateFeatures(candidates)

            log.info("predict candidates")
            val candidatesWithProba = predictCandidates(candidatesWithFeatures)

            log.info("getting top candidates")
            val bestCandidates = gettingBestCandidates(candidatesWithProba)

            log.info("predicting success!")
            return ArrayList(bestCandidates)

        } catch (e: Error) {
            val log = Logger.getLogger("error")
            log.severe("Error: $e")
            return arrayListOf()
        } catch (e: Exception) {
            val log = Logger.getLogger("exception")
            log.severe("Exception: $e")
            return arrayListOf()

        }
    }

    private fun generateCandidates(): List<ExtractionCandidate> {
        return if (analysisMethods != null) {
            val candidatesOfScope = CandidatesOfAnalysisMethods(analysisMethods!!)
            candidatesOfScope.getCandidates()
        } else {
            assert(analysisCandidates != null)
            CustomCandidates(analysisCandidates!!).getCandidates()
        }
    }

    private fun calculateFeatures(candidates: List<ExtractionCandidate>): List<CandidateWithFeatures> {
        val featuresOfEveryCandidate = FeaturesForEveryCandidate(candidates)
        return featuresOfEveryCandidate.getCandidatesWithFeatures()
    }

    private fun predictCandidates(candidatesWithFeature: List<CandidateWithFeatures>)
            : List<CandidatesWithFeaturesAndProba> {
        val model = TensorFlowModelProvider(candidatesWithFeature)
        return model.predictCandidates()
    }

    private fun gettingBestCandidates(candToProba: List<CandidatesWithFeaturesAndProba>)
            : List<CandidatesWithFeaturesAndProba> {
        val filter = GettingBestCandidates(ArrayList(candToProba))

        return filter.getTopKCandidates()
    }
}
