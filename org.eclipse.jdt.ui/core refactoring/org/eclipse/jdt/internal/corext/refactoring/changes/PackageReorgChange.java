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
package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.mapping.ResourceMapping;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.participants.ReorgExecutionLog;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.base.JDTChange;
import org.eclipse.jdt.internal.corext.refactoring.reorg.INewNameQuery;
import org.eclipse.jdt.internal.corext.util.JavaResourceMappings;

abstract class PackageReorgChange extends JDTChange {

	private String fPackageHandle;
	private String fDestinationHandle;
	private INewNameQuery fNameQuery;
	
	PackageReorgChange(IPackageFragment pack, IPackageFragmentRoot dest, INewNameQuery nameQuery){
		fPackageHandle= pack.getHandleIdentifier();
		fDestinationHandle= dest.getHandleIdentifier();
		fNameQuery= nameQuery;
	}
	
	abstract Change doPerformReorg(IProgressMonitor pm) throws JavaModelException;
	
	/* non java-doc
	 * @see IChange#perform(ChangeContext, IProgressMonitor)
	 */
	public final Change perform(IProgressMonitor pm) throws CoreException {
		pm.beginTask(getName(), 1);
		try {
			IPackageFragment pack= getPackage();
			ResourceMapping mapping= JavaResourceMappings.create(pack);
			final Change result= doPerformReorg(pm);
			markAsExecuted(pack, mapping);
			return result;
		} finally {
			pm.done();
		}
	}
	
	/* non java-doc
	 * @see IChange#getModifiedLanguageElement()
	 */
	public Object getModifiedElement() {
		return getPackage();
	}
	
	IPackageFragmentRoot getDestination(){
		return (IPackageFragmentRoot)JavaCore.create(fDestinationHandle);
	}
	
	IPackageFragment getPackage(){
		return (IPackageFragment)JavaCore.create(fPackageHandle);
	}

	String getNewName() {
		if (fNameQuery == null)
			return null;
		return fNameQuery.getNewName();
	}
	
	private void markAsExecuted(IPackageFragment pack, ResourceMapping mapping) {
		ReorgExecutionLog log= (ReorgExecutionLog)getAdapter(ReorgExecutionLog.class);
		if (log != null) {
			log.markAsProcessed(pack);
			log.markAsProcessed(mapping);
		}
	}
}

