package dataset_generation

import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.inline.InlineMethodProcessor
import java.util.logging.Logger

class InlineMethodsProcessor(
        pathToProjects: List<String>
) {

    private val log = Logger.getGlobal()!!
    var project: Project? = null
    var pathToProject: String? = null

    init {
        pathToProjects.forEach { analyze(it) }
    }

    private fun analyze(pathToProject: String) {
        try {
            this.pathToProject = pathToProject

            log.info(pathToProject)
            log.info("load project")
            loadProject()

            CommandProcessor.getInstance().executeCommand(project, {
                log.info("inner methods")
                innerMethods()
            }, null, null)

            ProjectManager.getInstance().closeProject(project!!)

        } catch (e: Exception) {
            print(e)
        } catch (e: Error) {
            print(e)
        }
    }

    private fun loadProject() {
        project = ProjectManager.getInstance().loadAndOpenProject(pathToProject!!)!!
    }

    private fun innerMethods() {
        val scope = AnalysisScope(project!!)
        scope.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethod(method: PsiMethod?) {
                super.visitMethod(method)

                val reference = getFirstReferenceOrNull(method!!) ?: return
                if (method.isConstructor || method.body == null || !method.isWritable)
                    return
                if (!method.isValid)
                    return
//                if (!InlineMethodProcessor.checkUnableToInsertCodeBlock())


                addBracketsToMethod(method)

                val editor = getEditor(reference)
                try {
                    InlineMethodProcessor(
                            reference.element.project, method, reference, editor, false
                    ).run()
                } catch (e: PsiInvalidElementAccessException) {
                    print(e)
                }
                EditorFactory.getInstance().releaseEditor(editor)
            }
        })
    }

    private fun getFirstReferenceOrNull(method: PsiMethod): PsiJavaCodeReferenceElement? {
        val query = ReferencesSearch.search(method)
        var totalRefs = 0
        var reference: PsiReference? = null
        query.forEach {
            if (totalRefs == 0) totalRefs++ else return null
            reference = it
        }
        return reference as? PsiJavaCodeReferenceElement
    }

    private fun getEditor(reference: PsiReference): Editor {
        val file = reference.element.containingFile
        val project = reference.element.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        return EditorFactory.getInstance().createEditor(document)!!
    }

    private fun addBracketsToMethod(sourceMethod: PsiMethod) {
        val factory = JavaPsiFacade.getInstance(project).getElementFactory()
        val startCandidateComment = factory.createCommentFromText("/*{*/", sourceMethod)
        val endCandidateComment = factory.createCommentFromText("/*}*/", sourceMethod)

        sourceMethod.body!!.addBefore(startCandidateComment, sourceMethod.body!!.firstBodyElement)
        sourceMethod.body!!.addAfter(endCandidateComment, sourceMethod.body!!.lastBodyElement)
    }

}