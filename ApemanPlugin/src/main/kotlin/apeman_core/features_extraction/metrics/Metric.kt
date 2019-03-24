package apeman_core.features_extraction.metrics

import apeman_core.features_extraction.calculators.SuperBaseCalculator
import apeman_core.pipes.CandidateWithFeatures

abstract class Metric(
        open val calculators: List<SuperBaseCalculator>
) {
    abstract fun fetchResult(candidates: List<CandidateWithFeatures>)
}
