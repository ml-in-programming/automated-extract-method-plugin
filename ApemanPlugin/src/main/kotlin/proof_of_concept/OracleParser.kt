package proof_of_concept

import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.PsiSubstitutorImpl
import com.intellij.util.io.isFile
import org.jetbrains.research.groups.ml_methods.utils.ExtractionCandidate
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger

class OracleParser(
        dirWithOracle: String,
        private val project: Project,
        private val scope: AnalysisScope
) {
    private val log = Logger.getLogger("OracleParser")
    init {
        log.level = Level.FINE
    }

    private val oraclePathStr = "$dirWithOracle/oracle.txt"
    private var entries = mutableListOf<OracleEntry>()

    fun parseOracle(): List<OracleEntry> {
        log.info("begin parsing")
        val oraclePath = Paths.get(oraclePathStr)

        assert(oraclePath.isFile())
        Files.lines(oraclePath).forEach {
            parseLine(it)
        }
        log.info("find all methods from oracle")
        findAllMethods()

        log.info("create candidates")
        createCandidates()
        return entries
    }

    private fun parseLine(line: String) {

        val args = line.split("\t").map { it.trim() }
        val methodName = "${args[0]}\t${args[1]}"

        val e = args[2].split(":")
        val startOffset = e[0].drop(1).toInt()
        val lengthOffset = e[1].dropLast(1).toInt()

        entries.add(OracleEntry(methodName, startOffset, lengthOffset))
    }

    private fun findAllMethods() {
        assert(entries.isNotEmpty())

        val methodsStr = entries.map { it.methodName }

        scope.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethod(method: PsiMethod?) {
                super.visitMethod(method)
                if (method == null)
                    return

                val methodName = getMethodSignature(method)
                val className = method.containingClass!!.qualifiedName ?: ""
                val ind = methodsStr.indexOf("$className\t$methodName")
                if (ind != -1) {
                    assert(entries[ind].method == null)
                    entries[ind].method = method
                }
            }
        })
        assert(entries.all { it.method != null })
    }

    private fun getMethodSignature(method: PsiMethod): String {
        val methodName = method.name
        log.info(methodName)

        val modifiersString = method.modifierList.children
                .filter { it is PsiKeyword }
                .map{ it.text }
                .joinToString(separator = " ")

        val returnType = method.returnType?.canonicalText ?: ""
        val parametersStr = method.parameterList.parameters
                .joinToString { it.type.canonicalText }

        var throws = method.throwsList.referencedTypes.joinToString { it.canonicalText }
        if (throws.isNotBlank())
            throws = "throws $throws"

        val line = "$modifiersString $returnType $methodName($parametersStr) $throws".trim()
        return line
    }

    private fun createCandidates() {
        for (entry in entries) {
            assert(entry.method != null)
            entry.method!!.accept(object : JavaRecursiveElementVisitor() {

                private var currentCodeBlock: PsiCodeBlock? = null
                private var currentMethod: PsiMethod? = null
                private var startOffset: Int? = null
                private var endOffset: Int? = null

                override fun visitMethod(method: PsiMethod?) {
                    val oldMethod = currentMethod
                    currentMethod = method

                    super.visitMethod(method)
                    currentMethod = oldMethod
                }

                override fun visitCodeBlock(block: PsiCodeBlock?) {
                    val oldBlock = currentCodeBlock
                    currentCodeBlock = block

                    super.visitCodeBlock(block)
                    currentCodeBlock = oldBlock
                }

                override fun visitComment(comment: PsiComment?) {
                    super.visitComment(comment)
                    if (comment == null)
                        return

                    if (comment.text!! == "{") {
                        startOffset = comment.textRange.endOffset
                        log.info("e$startOffset")
                    }

                    if (comment.text!! == "}") {

                        assert(startOffset != null)
                        endOffset = comment.textRange.startOffset
                        val candRange = TextRange(startOffset!!, endOffset!!)
                        log.info(":" + candRange.length)

                        val candStatements = currentCodeBlock!!.statements
                                .filter { candRange.contains(it.textRange) }
                        val cand = ExtractionCandidate(candStatements.toTypedArray(), currentMethod!!)
                        log.info(cand.toString())

                        entries.find { it.method == currentMethod!! }!!.candidate = cand
                        startOffset = null
                    }
                }
            })
        }
    }
}
