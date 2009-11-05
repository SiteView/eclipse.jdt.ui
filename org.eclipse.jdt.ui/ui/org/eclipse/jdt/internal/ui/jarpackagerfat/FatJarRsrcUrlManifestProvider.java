/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 219530 [jar application] add Jar-in-Jar ClassLoader option
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 262748 [jar exporter] extract constants for string literals in JarRsrcLoader et al.
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.ui.jarpackager.JarPackageData;

/**
 * A manifest provider creates manifest files for a fat jar with a JAR in JAR loader.
 * 
 * @since 3.5
 */
public class FatJarRsrcUrlManifestProvider extends FatJarManifestProvider {

	public FatJarRsrcUrlManifestProvider(FatJarRsrcUrlBuilder builder) {
		super(builder);
	}

	private void setManifestRsrcClasspath(Manifest ownManifest, JarPackageData jarPackage) {
		Set jarNames= new HashSet();
		Object[] elements= jarPackage.getElements();
		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			if (element instanceof IPackageFragmentRoot && ((IPackageFragmentRoot) element).isArchive()) {
				String jarName= ((IPackageFragmentRoot) element).getPath().toFile().getName();
				while (jarNames.contains(jarName)) {
					jarName= FatJarPackagerUtil.nextNumberedFileName(jarName);
				}
				jarNames.add(jarName);
			}
		}
		String manifestRsrcClasspath= getManifestRsrcClasspath(jarNames);
		ownManifest.getMainAttributes().putValue(JIJConstants.REDIRECTED_CLASS_PATH_MANIFEST_NAME, manifestRsrcClasspath); 
	}

	public String getManifestRsrcClasspath(Set jarNames) {
		StringBuffer result= new StringBuffer();
		result.append(JIJConstants.CURRENT_DIR); 
		for (Iterator iterator= jarNames.iterator(); iterator.hasNext();) {
			String jarName= (String) iterator.next();
			result.append(" ").append(jarName); //$NON-NLS-1$
		}
		return result.toString();
	}

	/**
	 * Hook for subclasses to add additional manifest entries.
	 * 
	 * @param	manifest	the manifest to which the entries should be added
	 * @param	jarPackage	the JAR package specification
	 */
	protected void putAdditionalEntries(Manifest manifest, JarPackageData jarPackage) {
		setManifestRsrcClasspath(manifest, jarPackage);
		putMainClass(manifest, jarPackage);
	}

	private void putMainClass(Manifest manifest, JarPackageData jarPackage) {
		if (jarPackage.getManifestMainClass() != null && jarPackage.getManifestMainClass().getFullyQualifiedName().length() > 0) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, JIJConstants.LOADER_MAIN_CLASS);
			manifest.getMainAttributes().putValue(JIJConstants.REDIRECTED_MAIN_CLASS_MANIFEST_NAME, jarPackage.getManifestMainClass().getFullyQualifiedName());
		}
	}

}
