package xtend

import org.eclipse.emf.common.util.URI;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.parser.IEncodingProvider;
import org.eclipse.xtext.resource.generic.AbstractGenericResourceRuntimeModule;

public class SystemCodeGeneratorModule extends AbstractGenericResourceRuntimeModule {

	public static class Utf8EncodingProvider implements IEncodingProvider {

		override getEncoding(URI uri) {
			return "UTF-8";
		}
	}

	override getLanguageName() {
		return "system";
	}

	override getFileExtensions() {
		return "xmi";
	}

	override public Class<? extends IEncodingProvider> bindIEncodingProvider() {
		return Utf8EncodingProvider;
	}

	def public Class<? extends IGenerator> bindIGenerator() {
		return SystemCodeGenerator;
	}

	def public Class<? extends ResourceSet> bindResourceSet() {
		return ResourceSetImpl;
	}

}
