package xtend

import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import java.io.File

/**
 * Some utility methods used in StatesMLCodeGeneratorStrings.
 */
class EclipseCodeGeneratorUtil {

	/**
	 * Given a file, recursively looks for the parent folder that contains
	 * an eclipse project, ie. containing a file named ".project".
	 */
	private static def File findParentEclipseProjectFolder(File f) {
		if (f.directory) {
			val projectFile = new File(f, ".project")
			if (projectFile.exists)
				return f
		}
		return findParentEclipseProjectFolder(f.parentFile)
	}

	/**
	 * Given an EObject, returns the name of the eclipse project 
	 * folder that contains its model file.
	 */
	public static def String getEclipseProjectName(EObject eObject) {
		val Resource eResource = eObject.eResource();
		val URI eUri = eResource.getURI();
		val String projectName = if (eUri.isPlatformResource || eUri.isPlatformPlugin) {
				eUri.segment(1);
			} else if (eUri.file) {
				val file = new File(eUri.toFileString)
				 findParentEclipseProjectFolder(file).name
			}
		return projectName;
	}
}
