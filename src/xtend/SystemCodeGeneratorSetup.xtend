package xtend

import com.google.inject.Guice
import org.eclipse.xtext.ISetup

public class SystemCodeGeneratorSetup implements ISetup {

	override createInjectorAndDoEMFRegistration() {
		return Guice.createInjector(new SystemCodeGeneratorModule());
	}

}
