package gui

import apeman_core.Launcher
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import java.util.logging.Logger

private val log = Logger.getGlobal()

class AnalysisScopeLauncher : BaseAnalysisAction("check1", "check2") {

    override fun analyze(project: Project, scope: AnalysisScope) {
        val launcher = Launcher(scope)
        val candidates = launcher.getCandidatesWithProba()
        showInfoDialog(candidates)
    }
}

class AnalysisMethodLauncher : AnAction("check only 1 method") {
    override fun actionPerformed(e: AnActionEvent?) {
        val caret = e!!.getData(CommonDataKeys.CARET)!!
        val file = e!!.getData(CommonDataKeys.PSI_FILE)!!

        val elem = file.findElementAt(caret.offset)
        val parents = elem!!.parents().toList()
        val method = parents.lastOrNull { it is PsiMethod }
        if (method == null) {
            log.warning("caret is not in method!")
            return
        }

        val launcher = Launcher(analysisMethods = listOf(method as PsiMethod))
        val candidates = launcher.getCandidatesWithProba()
        showInfoDialog(candidates)
    }
}

class AnalysisSelectionLauncher : AnAction("check only 1 selection of candidate") {
    override fun actionPerformed(e: AnActionEvent?) {
        val editor = e!!.getData(CommonDataKeys.EDITOR)!!
        val textRange = TextRange(
                editor.selectionModel.selectionStart,
                editor.selectionModel.selectionEnd)
        val file = e!!.getData(CommonDataKeys.PSI_FILE)!!
        val launcher = Launcher(analysisCandidates = listOf(textRange to file))
        val candidates = launcher.getCandidatesWithProba()
        showInfoDialog(candidates)
    }
}