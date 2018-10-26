import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.refactoring.extractMethod.ExtractMethodHandler

class CandidatesOfMethod(val sourceMethod: PsiMethod) {

    var candidates = ArrayList<Candidate>()

    init {
        fillCandidates()
        candidates.forEach{println(it)}
    }

    fun fillCandidates() {
        sourceMethod.accept(object : PsiRecursiveElementVisitor() {

            override fun visitElement(element: PsiElement?) {
                super.visitElement(element)
                if (element is PsiCodeBlock) {
                    generateCandidatesOfOneBlock(element)
                }
            }
        })
    }

    fun generateCandidatesOfOneBlock(block: PsiCodeBlock) {
        val n = block.statementCount

        for (i in 0 until n) {
            for (j in (i + 1) until n) {

                val candidate = Candidate(block.statements[i], block.statements[j], sourceMethod)
                if (isValid(candidate))
                    candidates.add(candidate)
            }
        }
    }

    // start virtual editor, select candidate and check if we can extract it
    fun isValid(candidate: Candidate): Boolean {
        val virtualEditor = FileEditorManager.getInstance(sourceMethod.project)
                .openFile(sourceMethod.containingFile.virtualFile, false)[0]

        if (virtualEditor == null || virtualEditor as? Editor == null)
            return false

        virtualEditor.selectionModel.setSelection(
                candidate.start.textOffset,
                candidate.end.textOffset
        )

        return ExtractMethodHandler().isAvailableForQuickList(
                virtualEditor,
                sourceMethod.containingFile,
                DataContext.EMPTY_CONTEXT
        )
    }
}