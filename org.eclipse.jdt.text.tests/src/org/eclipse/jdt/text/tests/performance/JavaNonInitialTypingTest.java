/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.text.tests.performance;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.test.performance.Performance;

/**
 * Measures the time to type in one single method into a large Java class
 * @since 3.1
 */
public class JavaNonInitialTypingTest extends NonInitialTypingTest {

	private static final Class THIS= JavaNonInitialTypingTest.class;
	
	public static Test suite() {
		return new PerformanceTestSetup(new TestSuite(THIS));
	}
	
	protected String getScenarioId() {
		String scenarioId= Performance.getDefault().getDefaultScenarioId(this);
		if ("org.eclipse.jdt.text.tests.performance.JavaNonInitialTypingTest#testTypeAMethod()".equals(scenarioId))
			return "org.eclipse.jdt.text.tests.performance.NonInitialTypingTest#testTypeAMethod()";
		return super.getScenarioId();
	}

	protected String getEditorId() {
		return "org.eclipse.jdt.ui.CompilationUnitEditor";
	}
}
