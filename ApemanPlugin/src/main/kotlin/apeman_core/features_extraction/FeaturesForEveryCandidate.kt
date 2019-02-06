package apeman_core.features_extraction

import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.project.Project
import com.sixrr.metrics.metricModel.MetricsResult
import com.sixrr.stockmetrics.candidateCalculators.RatioLocCandidateCalculator
import com.sixrr.stockmetrics.candidateMetrics.*
import com.sixrr.stockmetrics.methodMetrics.NumLiteralsMetric
import com.sixrr.stockmetrics.methodMetrics.NumTernaryOperatorsMetric
import com.sixrr.stockmetrics.methodMetrics.NumUsedTypesMetric
import org.jetbrains.research.groups.ml_methods.utils.ExtractionCandidate


class FeaturesForEveryCandidate(
        private val project: Project,
        private val analysisScope: AnalysisScope,
        private val candidates: ArrayList<ExtractionCandidate>
) {
    private val metrics: MutableList<Metric> = arrayListOf()
    private var calcRunner: FeaturesCalculationRunner? = null

    init {
        declareMetrics()
    }

    private fun declareMetrics() {

        metrics.addAll(listOf(
                CandidateMetric("Num_Literal", NumLiteralsCandidateMetric(candidates)),
                CandidateMetric("Num_Conditional", NumTernaryOperatorsCandidateMetric(candidates)),
                CandidateMetric("Num_Switch", NumSwitchOperatorsCandidateMetric(candidates)),
                CandidateMetric("Num_Type_Ac", NumTypeAccessesCandidateMetric(candidates))
        ))

        val namesToMetrics = metrics.map {
            it.name to it as CandidateMetric
        }.toMap()

        metrics.addAll(listOf(
                ComplementMetric("CON_LITERAL", NumLiteralsMetric(), namesToMetrics["Num_Literal"]!!),
                ComplementMetric("CON_CONDITIONAL", NumTernaryOperatorsMetric(), namesToMetrics["Num_Conditional"]!!),
                ComplementMetric("CON_TYPE_ACC", NumUsedTypesMetric(), namesToMetrics["Num_Type_Ac"]!!),
                CandidateMetric("ratio_LOC", RatioLocCandidateMetric(candidates)),
                CandidateMetric("Ratio_Variable_Access", VariableCouplingCandidateMetric(candidates)),
                CandidateMetric("Ratio_Variable_Access2", VariableCoupling2CandidateMetric(candidates)),
                CandidateMetric("VarAc_Cohesion", VariableCohesionCandidateMetric(candidates)),
                CandidateMetric("VarAc_Cohesion2", VariableCohesion2CandidateMetric(candidates)),
                CandidateMetric("Ratio_Field_Access", FieldCouplingCandidateMetric(candidates)),
                CandidateMetric("Ratio_Field_Access2", FieldCoupling2CandidateMetric(candidates)),
                CandidateMetric("Field_Cohesion", FieldCohesionCandidateMetric(candidates)),
                CandidateMetric("Field_Cohesion2", FieldCohesion2CandidateMetric(candidates)),
                MaxFrom2Metric("Ratio_Invocation", InvocationCouplingCandidateMetric(candidates), InvocationNewCouplingCandidateMetric(candidates)),
                MaxFrom2Metric("Invocation_Cohesion", InvocationCohesionCandidateMetric(candidates), InvocationNewCohesionCandidateMetric(candidates))
        ))
    }

    private fun calculate() {
        assert(metrics.isNotEmpty())

        calcRunner = FeaturesCalculationRunner(project, analysisScope, metrics)
        calcRunner!!.calculate()
    }

    fun getCandidatesWithFeatures(): List<CandidateWithFeatures> {
        calculate()
        val candResults = calcRunner!!.resultsForCandidates!!
        val methodResults = calcRunner!!.resultsForMethods!!
        val candWithFeatures = arrayListOf<CandidateWithFeatures>()

        for (cand in candidates) {
            val featureVector =  getFeatureVector(
                    cand, candResults, methodResults
            )
            candWithFeatures.add(CandidateWithFeatures(cand, featureVector))
        }
        return candWithFeatures
    }

    private fun getFeatureVector(
            cand: ExtractionCandidate,
            candResults: MetricsResult,
            methodResults: MetricsResult
    ): List<Feature> {

        val featureVector = arrayListOf<Feature>()
        for (m in metrics) {
            val value = m.calculateResult(cand, candResults, methodResults)
            featureVector.add(Feature(m.name, value))
        }
        return featureVector
    }

//    private val gemsMetrics = mutableListOf<Metric>(
//
//            // method metrics
//            NumStatementsMetric(),
//            NumLocalVarsMetric(),
//            NumLiteralsMetric(),
//            NumTernaryOperatorsMetric(),
//            NumAssertsMetric(),
//            NumAssignmentsMetric(),
//            NumUsedTypesMetric(),
//            NumUsedPackagesMetric(),
//            NumMethodCallsMetric(),
//            NumIfMetric(),
//            NumLocalVarsAccessMetric(),
//
//            // candidate metrics
//            NumLiteralsCandidateMetric(candidates),
//            NumTernaryOperatorsCandidateMetric(candidates),
//            NumSwitchOperatorsCandidateMetric(candidates)
//    )
}
