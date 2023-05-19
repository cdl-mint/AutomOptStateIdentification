package xtend;

import org.eclipse.xtext.resource.generic.AbstractGenericResourceSupport

public class SystemCodeGeneratorSupport extends AbstractGenericResourceSupport {

	override createGuiceModule() {
		return new SystemCodeGeneratorModule();
	}

}
