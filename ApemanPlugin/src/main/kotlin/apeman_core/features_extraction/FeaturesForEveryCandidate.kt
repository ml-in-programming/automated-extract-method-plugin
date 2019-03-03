package apeman_core.features_extraction

import apeman_core.base_entities.ExtractionCandidate
import apeman_core.base_entities.FeatureType
import apeman_core.pipes.CandidateWithFeatures
import apeman_core.base_entities.Features
import apeman_core.features_extraction.calculators.BaseMetricsCalculator
import apeman_core.features_extraction.calculators.candidate.*
import apeman_core.features_extraction.calculators.method.*
import apeman_core.features_extraction.metrics.CandidateMetric
import apeman_core.features_extraction.metrics.ComplementMetric
import apeman_core.features_extraction.metrics.MaxFrom2Metric
import apeman_core.features_extraction.metrics.Metric
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.util.containers.mapSmart
import com.sixrr.metrics.metricModel.MetricsResult


class FeaturesForEveryCandidate(
        private val project: Project,
        private val analysisScope: AnalysisScope,
        candidates: ArrayList<ExtractionCandidate>
) {
    private val candidates = ArrayList(candidates.map { CandidateWithFeatures(it) })
    private val metrics: MutableList<Metric> = arrayListOf()
    private var calcRunner: FeaturesCalculationRunner? = null

    init {
        declareMetrics()
    }

    private fun declareMetrics() {

        val candidateCalculators = listOf(
                NumLiteralsCandidateCalculator(candidates),
                NumTernaryOperatorsCandidatesCalculator(candidates),
                NumTypeAccessesCandidateCalculator(candidates),
                NumInvocationsCandidateCalculator(candidates),
                NumIfCandidateCalculator(candidates),
                NumAssignmentsCandidateCalculator(candidates),
                NumSwitchOperatorsCandidatesCalculator(candidates),
                NumLocalVarsCandidateCalculator(candidates),
                NumFieldAccessCandidateCalculator(candidates),
                NumVarsAccessCandidateCalculator(candidates),
                NumTypedElementsCandidateCalculator(candidates),
                NumPackageAccessesCandidateCalculator(candidates),
                LocCandidateCalculator(candidates),
                RatioLocCandidateCalculator(candidates),

                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.VAR_ACCESS_COUPLING, true, true, PsiVariable::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.VAR_ACCESS_COUPLING_2, true, false, PsiVariable::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.VAR_ACCESS_COHESION, false, true, PsiVariable::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.VAR_ACCESS_COHESION_2, false, false, PsiVariable::class.java),

                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.FIELD_ACCESS_COUPLING, true, true, PsiField::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.FIELD_ACCESS_COUPLING_2, true, false, PsiField::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.FIELD_ACCESS_COHESION, false, true, PsiField::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.FIELD_ACCESS_COHESION_2, false, false, PsiField::class.java),

//                TypeAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.TYPE_ACCESS_COUPLING, true, true),
//                TypeAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.TYPE_ACCESS_COUPLING_2, true, false),
//                TypeAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.TYPE_ACCESS_COHESION, false, true),
//                TypeAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.TYPE_ACCESS_COHESION_2, false, false),

                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.TYPED_ELEMENTS_COUPLING, true, true, PsiTypeElement::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.TYPED_ELEMENTS_COHESION, false, true, PsiTypeElement::class.java),

                PackageAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.PACKAGE_COUPLING, true, true),
                PackageAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.PACKAGE_COUPLING_2, true, false),
                PackageAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.PACKAGE_COHESION, false, true),
                PackageAccessCouplingCohesionCandidateCalculator(candidates, FeatureType.PACKAGE_COHESION_2, false, false)
        )

        val complementCalculators = listOf<BaseMetricsCalculator>(
            NumLiteralsMethodCalculator(candidates),
            NumTernaryMethodCalculator(candidates),
            NumUsedTypesMethodCalculator(candidates),
            NumInvocationMethodCalculator(candidates),
            NumIfMethodCalculator(candidates),
            NumAssignmentsMethodCalculator(candidates),
            NumSwitchMethodCalculator(candidates),
            NumLocalVarsAccessMethodCalculator(candidates),
            NumFieldAccessMethodCalculator(candidates),
            NumLocalVarsMethodCalculator(candidates),
            NumTypedElementsMethodCalculator(candidates),
            NumUsedPackagesMethodCalculator(candidates)
        )

        val invocationMetricCoupling = MaxFrom2Metric(listOf(
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.INVOCATION_COUPLING, true, true, PsiCallExpression::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.INVOCATION_COUPLING, true, true, PsiNewExpression::class.java)
        ))

        val invocationMetricCohesion = MaxFrom2Metric(listOf(
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.INVOCATION_COHESION, false, true, PsiCallExpression::class.java),
                AbstractCouplingCohesionCandidateCalculator(candidates, FeatureType.INVOCATION_COHESION, false, true, PsiNewExpression::class.java)
        ))

        val candidateMetrics = candidateCalculators.map { CandidateMetric(it) }

        val complementMetrics = complementCalculators
                .mapIndexed { i, calc -> ComplementMetric(listOf(calc, candidateCalculators[i])) }

        metrics.addAll(listOf(
                candidateMetrics,
                complementMetrics,
                listOf(invocationMetricCoupling,
                        invocationMetricCohesion)
        ).flatten())
    }

    private fun calculate() {
        assert(metrics.isNotEmpty())
        candidates.forEach { cand -> cand.features.forEach { cand.features[it.key] = -1.0 } }

        calcRunner = FeaturesCalculationRunner(project, analysisScope, metrics)
        calcRunner!!.calculate()

        assert(candidates.all { it.features.all { it.value != -1.0 } })
    }

    fun getCandidatesWithFeatures(): List<CandidateWithFeatures> {
        calculate()
        val candResults = calcRunner!!.resultsForCandidates!!
        val methodResults = calcRunner!!.resultsForMethods!!
        val candWithFeatures = arrayListOf<CandidateWithFeatures>()

        for (cand in candidates) {
            val featureVector = getFeatureVector(
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
    ): List<Features> {

        val featureVector = arrayListOf<Features>()
        for (m in metrics) {
            val value = m.calculateResult(cand, candResults, methodResults)
            featureVector.add(Features(m.name, value))
        }
        return featureVector
    }
}
