/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.surround;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportEdit;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.CodeScopeBuilder;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.textmanipulation.GroupDescription;
import org.eclipse.jdt.internal.corext.textmanipulation.MultiTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;

/**
 * Surround a set of statements with a try/catch block.
 * 
 * Special case:
 * 
 * URL url= file.toURL();
 * 
 * In this case the variable declaration statement gets convert into a
 * declaration without initializer. So the body of the try/catch block 
 * only consists of new assignements. In this case we can't move the 
 * selected nodes (e.g. the declaration) into the try block.
 */
public class SurroundWithTryCatchRefactoring extends Refactoring {

	private Selection fSelection;
	private CodeGenerationSettings fSettings;
	private ISurroundWithTryCatchQuery fQuery;
	private SurroundWithTryCatchAnalyzer fAnalyzer;
	private boolean fSaveChanges;

	private ICompilationUnit fCUnit;
	private CompilationUnit fRootNode;
	private ASTRewrite fRewriter;
	private ImportEdit fImportEdit;
	private CodeScopeBuilder.Scope fScope;
	private ASTNode fSelectedNode;
	private List fStatementsOfSelectedNode;
	private List fTryBody;

	private SurroundWithTryCatchRefactoring(ICompilationUnit cu, Selection selection, CodeGenerationSettings settings, ISurroundWithTryCatchQuery query) {
		fCUnit= cu;
		fSelection= selection;
		fSettings= settings;
		fQuery= query;
	}

	public static SurroundWithTryCatchRefactoring create(ICompilationUnit cu, ITextSelection selection, CodeGenerationSettings settings, ISurroundWithTryCatchQuery query) {
		return new SurroundWithTryCatchRefactoring(cu, Selection.createFromStartLength(selection.getOffset(), selection.getLength()), settings, query);
	}
		
	public static SurroundWithTryCatchRefactoring create(ICompilationUnit cu, int offset, int length, CodeGenerationSettings settings, ISurroundWithTryCatchQuery query) {
		return new SurroundWithTryCatchRefactoring(cu, Selection.createFromStartLength(offset, length), settings, query);
	}

	public void setSaveChanges(boolean saveChanges) {
		fSaveChanges= saveChanges;
	}
	
	public boolean stopExecution() {
		if (fAnalyzer == null)
			return true;
		ITypeBinding[] exceptions= fAnalyzer.getExceptions();
		return exceptions == null || exceptions.length == 0;
	}
	
	/* non Java-doc
	 * @see IRefactoring#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.getString("SurroundWithTryCatchRefactoring.name"); //$NON-NLS-1$
	}

	public RefactoringStatus checkActivationBasics(CompilationUnit rootNode, IProgressMonitor pm) throws JavaModelException {
		RefactoringStatus result= new RefactoringStatus();
		fRootNode= rootNode;
			
		fAnalyzer= new SurroundWithTryCatchAnalyzer(fCUnit, fSelection, fQuery);
		fRootNode.accept(fAnalyzer);
		result.merge(fAnalyzer.getStatus());
		return result;
	}


	/*
	 * @see Refactoring#checkActivation(IProgressMonitor)
	 */
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException {
		RefactoringStatus result= new RefactoringStatus();
		result.merge(Checks.validateModifiesFiles(ResourceUtil.getFiles(new ICompilationUnit[]{fCUnit})));
		if (result.hasFatalError())
			return result;
		
		CompilationUnit rootNode= AST.parseCompilationUnit(fCUnit, true);
		return checkActivationBasics(rootNode, pm);
	}

	/*
	 * @see Refactoring#checkInput(IProgressMonitor)
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		return new RefactoringStatus();
	}

	/* non Java-doc
	 * @see IRefactoring#createChange(IProgressMonitor)
	 */
	public IChange createChange(IProgressMonitor pm) throws JavaModelException {
		final String NN= ""; //$NON-NLS-1$
		TextBuffer buffer= null;
		try {
			CompilationUnitChange result= new CompilationUnitChange(getName(), fCUnit);
			result.setSave(fSaveChanges);
			MultiTextEdit root= new MultiTextEdit();
			result.setEdit(root);
			buffer= TextBuffer.acquire(getFile());
			ASTNodes.expandRange(fAnalyzer.getSelectedNodes(), buffer, fSelection.getOffset(), fSelection.getLength());
			fRewriter= new ASTRewrite(fAnalyzer.getEnclosingBodyDeclaration());
			fImportEdit= new ImportEdit(fCUnit, fSettings);
			
			fScope= CodeScopeBuilder.perform(fAnalyzer.getEnclosingBodyDeclaration(), fSelection).
				findScope(fSelection.getOffset(), fSelection.getLength());
			fScope.setCursor(fSelection.getOffset());
			
			computeTargetNode();
			
			fTryBody= new ArrayList(2);
			List newStatements= createLocals();
			newStatements.add(createTryCatchStatement(buffer.getLineDelimiter()));
			if (newStatements.size() == 1) {
				fRewriter.markAsReplaced(fSelectedNode, (ASTNode)newStatements.get(0));
			} else {
				List container= getSelectedNodeContainer();
				if (selectedNodeIsDeclaration()) {
					int index= container.indexOf(fSelectedNode);
					for (Iterator iter= newStatements.iterator(); iter.hasNext();) {
						ASTNode element= (ASTNode)iter.next();
						fRewriter.markAsInserted(element);
						container.add(++index, element);
					}
				} else {
					fRewriter.markAsReplaced(fSelectedNode, container, 
						(ASTNode[])newStatements.toArray(new ASTNode[newStatements.size()]));
				}
			}
			
			if (!fImportEdit.isEmpty()) {
				root.add(fImportEdit);
				result.addGroupDescription(new GroupDescription(NN, new TextEdit[] {fImportEdit} ));
			}
			MultiTextEdit change= new MultiTextEdit();
			root.add(change);
			fRewriter.rewriteNode(buffer, change);
			result.addGroupDescription(new GroupDescription(NN, new TextEdit[] {change} ));
			return result;
		} catch (JavaModelException e) {
			throw e;
		} catch (CoreException e) {
			throw new JavaModelException(e);
		} finally {
			fRewriter.removeModifications();
			if (buffer != null)
				TextBuffer.release(buffer);
		}
	}
	
	private AST getAST() {
		return fRootNode.getAST();
	}
	
	private void computeTargetNode() {
		ASTNode[] nodes= fAnalyzer.getSelectedNodes();
		if (nodes.length == 1) {
			fSelectedNode= nodes[0];
		} else {
			List container= ASTNodes.getContainingList(nodes[0]);
			fSelectedNode= fRewriter.collapseNodes(container, container.indexOf(nodes[0]), nodes.length);
		}
	}
	
	private List getStatementsOfSelectedNode() {
		if (fStatementsOfSelectedNode != null)
			return fStatementsOfSelectedNode;
			
		if (fRewriter.isCollapsed(fSelectedNode)) {
			fStatementsOfSelectedNode= fRewriter.getCollapsedNodes(fSelectedNode); 	
		} else {
			fStatementsOfSelectedNode= ASTNodes.getContainingList(fSelectedNode);
			if (fStatementsOfSelectedNode == null) {
				Block block= getAST().newBlock();
				fStatementsOfSelectedNode= block.statements();
				fStatementsOfSelectedNode.add(fRewriter.createCopy(fSelectedNode));
				fRewriter.markAsRemoved(fSelectedNode);
			}
		}
		return fStatementsOfSelectedNode;
	}
	
	private List getSelectedNodeContainer() {
		List result= ASTNodes.getContainingList(fSelectedNode);
		if (result != null)
			return result;
		return getStatementsOfSelectedNode();
	}
	
	private List createLocals() {
		List result= new ArrayList(3);
		final List locals= new ArrayList(Arrays.asList(fAnalyzer.getAffectedLocals()));
		if (locals.size() > 0) {
			final VariableDeclarationStatement[] statements= getStatements(locals);
			for (int i= 0; i < statements.length; i++) {
				VariableDeclarationStatement st= statements[i];
				result.addAll(handle(st, locals));
			}
		}
		return result;
	}
	
	private VariableDeclarationStatement[] getStatements(List locals) {
		List result= new ArrayList(locals.size());
		for (int i= 0; i < locals.size(); i++) {
			ASTNode parent= ((ASTNode)locals.get(i)).getParent();
			if (parent instanceof VariableDeclarationStatement && !result.contains(parent))
				result.add(parent);
		}
		return (VariableDeclarationStatement[])result.toArray(new VariableDeclarationStatement[result.size()]);
	}
	
	private List handle(VariableDeclarationStatement statement, List locals) {
		boolean isSelectedNode= statement == fSelectedNode;
		List result= new ArrayList();
		List fragments= statement.fragments();
		result.add(fRewriter.createCopy(statement));
		List container= getStatementsOfSelectedNode();
		AST ast= getAST();
		List newAssignments= new ArrayList(2);
		for (Iterator iter= fragments.iterator(); iter.hasNext();) {
			VariableDeclarationFragment fragment= (VariableDeclarationFragment)iter.next();
			Expression initializer= fragment.getInitializer();
			if (initializer != null) {
				Assignment assignment= ast.newAssignment();
				assignment.setLeftHandSide((Expression)ASTNode.copySubtree(ast, fragment.getName()));
				assignment.setRightHandSide((Expression)fRewriter.createCopy(initializer));
				fRewriter.markAsRemoved(initializer);
				ExpressionStatement es= ast.newExpressionStatement(assignment);
				if (isSelectedNode) {
					fTryBody.add(es);
				} else {
					newAssignments.add(es);
				}
			}
		}
		fRewriter.markAsReplaced(statement, container, (ASTNode[])newAssignments.toArray(new ASTNode[newAssignments.size()]));
		return result;
	}
	
	private TryStatement createTryCatchStatement(String lineDelimiter) throws CoreException {
		TryStatement tryStatement= getAST().newTryStatement();
		ITypeBinding[] exceptions= fAnalyzer.getExceptions();
		for (int i= 0; i < exceptions.length; i++) {
			ITypeBinding exception= exceptions[i];
			String type= fImportEdit.addImport(exception);
			CatchClause catchClause= getAST().newCatchClause();
			tryStatement.catchClauses().add(catchClause);
			SingleVariableDeclaration decl= getAST().newSingleVariableDeclaration();
			String name= fScope.createName("e", false); //$NON-NLS-1$
			decl.setName(getAST().newSimpleName(name));
			decl.setType(ASTNodeFactory.newType(getAST(), type));
			catchClause.setException(decl);
			Statement st= getCatchBody(type, name, lineDelimiter);
			if (st != null) {
				catchClause.getBody().statements().add(st);
			}
		}
		List statements= tryStatement.getBody().statements();
		if (selectedNodeIsDeclaration()) {
			statements.addAll(fTryBody);
		} else {
			statements.add(fRewriter.createCopy(fSelectedNode));
		}
		return tryStatement;
	}
	
	private Statement getCatchBody(String type, String name, String lineSeparator) throws CoreException {
		String s= StubUtility.getCatchBodyContent(fCUnit, type, name, lineSeparator);
		if (s == null) {
			return null;
		} else {
			return (Statement)fRewriter.createPlaceholder(s, ASTRewrite.STATEMENT);
		}
	}
	
	private IFile getFile() throws JavaModelException {
		if (fCUnit.isWorkingCopy())
			return (IFile)fCUnit.getOriginalElement().getResource();
		else
			return (IFile)fCUnit.getResource();
	}
	
	private boolean selectedNodeIsDeclaration() {
		return fTryBody.size() > 0; 
	}	
}
