package xtend;

import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.generator.IFileSystemAccess
import org.eclipse.xtext.generator.IGenerator
import eMISA_Xtend.System
import eMISA_Xtend.State
import eMISA_Xtend.Block
import eMISA_Xtend.StateMachine

/**
 * Code generator for automated System.
 */
public class SystemCodeGenerator implements IGenerator {

	/**
	 * Method automatically called by the MWE2 script of workflow
	 * It is called one time per .xmi file of EMISA_Xtend/model.
	 */
	override doGenerate(Resource input, IFileSystemAccess fsa) {

		// We assume that the input file (as a Resource) contains a single root System
		val System root = input.contents.get(0) as System
		for (b : root.block) {
			if (b.statemachine !== null) {
				fsa.generateFile('''timeSeries/StateQuery.java''', generateStateFile(b.statemachine, b))
			}
		}

	}

	private static def generateStateFile(StateMachine sm, Block b) {
		'''
		package timeSeries;
		
		public class StateQuery {
			public static String createQuery(String state){
				String result ="";
				«FOR s : sm.state»
				if(state.equals("«s.name»")){
					result = «generateQuery(s, b)»;
					}
				«ENDFOR»
				return result;
			}
		}
		'''

	}

	private static def generateQuery(State state, Block b) {
		'''"SELECT «FOR a : state.assignment SEPARATOR ','» «a.property.name» «ENDFOR», time"
			+ " FROM «b.name»"
			+ " WHERE «FOR a : state.assignment SEPARATOR ' AND '» «a.property.name»>=«a.value-a.property.tolerance.value» AND «a.property.name»<=«a.value+a.property.tolerance.value»«ENDFOR»";
		'''
	}
}
