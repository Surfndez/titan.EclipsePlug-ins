/******************************************************************************
 * Copyright (c) 2000-2019 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.definitions;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jface.text.templates.Template;
import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.GovernedSimple.CodeSectionType;
import org.eclipse.titan.designer.AST.INamedNode;
import org.eclipse.titan.designer.AST.IReferenceChain;
import org.eclipse.titan.designer.AST.ISubReference;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.IType.TypeOwner_type;
import org.eclipse.titan.designer.AST.IType.Type_type;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.NamedBridgeScope;
import org.eclipse.titan.designer.AST.NamingConventionHelper;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.AST.ReferenceChain;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.Type;
import org.eclipse.titan.designer.AST.TTCN3.TemplateRestriction;
import org.eclipse.titan.designer.AST.TTCN3.TemplateRestriction.Restriction_type;
import org.eclipse.titan.designer.AST.TTCN3.definitions.FormalParameter.parameterEvaluationType;
import org.eclipse.titan.designer.AST.TTCN3.templates.ITTCN3Template;
import org.eclipse.titan.designer.AST.TTCN3.templates.ITTCN3Template.Template_type;
import org.eclipse.titan.designer.AST.TTCN3.templates.PatternString.PatternType;
import org.eclipse.titan.designer.AST.TTCN3.templates.TTCN3Template;
import org.eclipse.titan.designer.AST.TTCN3.templates.UnivCharString_Pattern_Template;
import org.eclipse.titan.designer.AST.TTCN3.types.Array_Type;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.editors.ProposalCollector;
import org.eclipse.titan.designer.editors.T3Doc;
import org.eclipse.titan.designer.editors.actions.DeclarationCollector;
import org.eclipse.titan.designer.editors.ttcn3editor.TTCN3CodeSkeletons;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.IIdentifierReparser;
import org.eclipse.titan.designer.parsers.ttcn3parser.IdentifierReparser;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;
import org.eclipse.titan.designer.preferences.PreferenceConstants;

/**
 * The Def_Template class represents TTCN3 template definitions.
 *
 * @author Kristof Szabados
 * @author Arpad Lovassy
 */
public final class Def_Template extends Definition implements IParameterisedAssignment {
	private static final String FULLNAMEPART1 = ".<type>";
	private static final String FULLNAMEPART2 = ".<formal_parameter_list>";
	private static final String FULLNAMEPART3 = ".<derived_reference>";

	public static final String PORTNOTALLOWED = "Template can not be defined for port type `{0}''";
	public static final String TEMPLATEREFERENCEEXPECTEDINMODIFIES = "Reference to a template was expected"
			+ " in the `modifies'' definition instead of {0}";
	public static final String IMCOMPATIBLEBASETYPE = "The modified template has different type than the base template `{0}'':"
			+ " `{1}'' was expected instead of `{2}'";
	public static final String FEWERFORMALPARAMETERS = "The modified template has fewer formal parameters than base template `{0}'':"
			+ " at least {1} parameter was expected instead of {2}";
	public static final String DIFFERENTPARAMETERKINDS = "The kind of parameter is not the same as in base template `{0}'':"
			+ " {1} was expected instead of {2}";
	public static final String INCOMPATIBLEBASEPARAMETERTYPE = "The type of parameter is not the same as in base template `{0}'':"
			+ " `{1}'' was expected instead of `{2}''";
	public static final String DIFFERENTPARAMETERNAMES = "The name of parameter is not the same as in base template `{0}'':"
			+ " `{1}'' was expected instead of `{2}''";
	public static final String CIRCULARBASETEMPLATES = "Circular chain of base templates: `{0}''";
	public static final String CIRCULAREMBEDDEDRECURSION = "Embedded circular recursion chain: `{0}''";

	private static final String WITHTEMPRESTNOTALLOWED = "Formal parameter with template restriction `{0}'' not allowed here";
	private static final String WITHOUTTEMPRESTNOTALLOWED = "Formal parameter without template restriction not allowed here";
	private static final String NOBASETEMPLATEPARFORDASH = "Not used symbol (`-') doesn't have the corresponding default parameter"
			+ " in the base template";
	private static final String NOBASETEMPLATEFORDASH = "Only modified templates are allowed to use the not used symbol (`-')"
			+ " as the default parameter";
	private static final String PARAMETRIZED_LOCAL_TEMPLATE = "Code generation for parameterized local template `{0}'' is not yet supported";

	private static final String KIND = " template";

	public static String getKind() {
		return KIND;
	}

	private final Type type;

	private final TemplateRestriction.Restriction_type templateRestriction;
	private boolean generateRestrictionCheck = false;

	/**
	 * The formal parameter list. Can be null, in that case this template is
	 * not parameterized
	 * */
	private final FormalParameterList formalParList;

	/**
	 * points to the base template reference in case of modified templates,
	 * otherwise it is NULL.
	 */
	private final Reference derivedReference;

	/** the body of the template. */
	private final TTCN3Template body;

	private ITTCN3Template realBody;

	/**
	 * the base template of the actual one in case it modifies one, Used as
	 * a cache only.
	 */
	private Def_Template baseTemplate;

	/**
	 * The last time when recursive derivation was checked for this
	 * template.
	 */
	private CompilationTimeStamp recursiveDerivationChecked;
	private NamedBridgeScope bridgeScope = null;

	/**
	 * normal, lazy or fuzzy evaluation should be used.
	 */
	private final parameterEvaluationType evaluationType;

	public Def_Template( final TemplateRestriction.Restriction_type templateRestriction,
			final Identifier identifier,
			final Type type,
			final FormalParameterList formalParList,
			final Reference derivedReference,
			final TTCN3Template body,
			final parameterEvaluationType evaluationType ) {
		super(identifier);
		this.templateRestriction = templateRestriction;
		this.type = type;
		this.formalParList = formalParList;
		this.derivedReference = derivedReference;
		this.body = body;
		this.evaluationType = evaluationType;

		if (type != null) {
			type.setOwnertype(TypeOwner_type.OT_TEMPLATE_DEF, this);
			type.setFullNameParent(this);
		}
		if (formalParList != null) {
			formalParList.setMyDefinition(this);
		}
		if (derivedReference != null) {
			derivedReference.setFullNameParent(this);
		}
		if (body != null) {
			body.setFullNameParent(this);
		}
	}

	@Override
	/** {@inheritDoc} */
	public Assignment_type getAssignmentType() {
		return Assignment_type.A_TEMPLATE;
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = super.getFullName(child);

		if (type == child) {
			return builder.append(FULLNAMEPART1);
		} else if (formalParList == child) {
			return builder.append(FULLNAMEPART2);
		} else if (derivedReference == child) {
			return builder.append(FULLNAMEPART3);
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public FormalParameterList getFormalParameterList() {
		return formalParList;
	}

	@Override
	/** {@inheritDoc} */
	public String getAssignmentName() {
		return "template";
	}

	@Override
	/** {@inheritDoc} */
	public String getDescription() {
		final StringBuilder builder = new StringBuilder();
		builder.append(getAssignmentName()).append(" `");

		if (isLocal()) {
			builder.append(identifier.getDisplayName());
		} else {
			builder.append(getFullName());
		}

		builder.append('\'');
		return builder.toString();
	}

	@Override
	/** {@inheritDoc} */
	public String getOutlineIcon() {
		return "template.gif";
	}

	@Override
	/** {@inheritDoc} */
	public int category() {
		int result = super.category();
		if (type != null) {
			result += type.category();
		}
		return result;
	}

	@Override
	/** {@inheritDoc} */
	public String getOutlineText() {
		final StringBuilder text = new StringBuilder(identifier.getDisplayName());
		if (formalParList == null || lastTimeChecked == null) {
			return text.toString();
		}

		text.append('(');
		for (int i = 0; i < formalParList.getNofParameters(); i++) {
			if (i != 0) {
				text.append(", ");
			}

			final FormalParameter parameter = formalParList.getParameterByIndex(i);
			if (Assignment_type.A_PAR_TIMER.semanticallyEquals(parameter.getRealAssignmentType())) {
				text.append("timer");
			} else {
				final IType parameterType = parameter.getType(lastTimeChecked);
				if (parameterType == null) {
					text.append("Unknown type");
				} else {
					text.append(parameterType.getTypename());
				}
			}
		}
		text.append(')');
		return text.toString();
	}

	@Override
	/** {@inheritDoc} */
	public ITTCN3Template getSetting(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked == null) {
			check(timestamp);
		}

		return realBody;
	}

	@Override
	/** {@inheritDoc} */
	public IType getType(final CompilationTimeStamp timestamp) {
		check(timestamp);

		return type;
	}

	/**
	 * Check the template, tries to calculate the real template body and
	 * returns it.
	 * 
	 * @param timestamp
	 *                the time stamp of the actual semantic check cycle.
	 *
	 * @return the template if it exists, otherwise null
	 * */
	public ITTCN3Template getTemplate(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked == null) {
			check(timestamp);
		}

		return realBody;
	}

	/**
	 * Check the template and returns it's formal parameter list.
	 * 
	 * @param timestamp
	 *                the time stamp of the actual semantic check cycle.
	 *
	 * @return the formal parameter list if it exists, otherwise null
	 * */
	public FormalParameterList getFormalParameterList(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked == null) {
			check(timestamp);
		}

		return formalParList;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		if (bridgeScope != null && bridgeScope.getParentScope() == scope) {
			return;
		}

		bridgeScope = new NamedBridgeScope();
		bridgeScope.setParentScope(scope);
		scope.addSubScope(getLocation(), bridgeScope);
		bridgeScope.setScopeMacroName(identifier.getDisplayName());

		super.setMyScope(bridgeScope);
		if (type != null) {
			type.setMyScope(bridgeScope);
		}
		if (formalParList != null) {
			formalParList.setMyScope(bridgeScope);
			bridgeScope.addSubScope(formalParList.getLocation(), formalParList);
			if (body != null) {
				body.setMyScope(formalParList);
			}
		} else if (body != null) {
			body.setMyScope(bridgeScope);
		}
		if (derivedReference != null) {
			derivedReference.setMyScope(bridgeScope);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		check(timestamp, null);
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp, final IReferenceChain refChain) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return;
		}
		lastTimeChecked = timestamp;
		T3Doc.check(this.getCommentLocation(), KIND);

		isUsed = false;

		if (isLocal()) {
			NamingConventionHelper.checkConvention(PreferenceConstants.REPORTNAMINGCONVENTION_LOCAL_TEMPLATE, identifier, this);
		} else {
			NamingConventionHelper.checkConvention(PreferenceConstants.REPORTNAMINGCONVENTION_GLOBAL_TEMPLATE, identifier, this);
		}
		NamingConventionHelper.checkNameContents(identifier, getMyScope().getModuleScope().getIdentifier(), getDescription());

		if (type == null) {
			return;
		}

		type.setGenName("_T_", getGenName());
		type.check(timestamp, refChain);

		if (withAttributesPath != null) {
			withAttributesPath.checkGlobalAttributes(timestamp, true);
			withAttributesPath.checkAttributes(timestamp, type.getTypeRefdLast(timestamp, refChain).getTypetype());
		}

		if (body == null) {
			return;
		}

		final IType lastType = type.getTypeRefdLast(timestamp);
		switch (lastType.getTypetype()) {
		case TYPE_PORT:
			location.reportSemanticError(MessageFormat.format(PORTNOTALLOWED, lastType.getFullName()));
			break;
		default:
			break;
		}
		body.setMyGovernor(type);

		realBody = body;

		// Needed in case of universal charstring templates
		if (body.getTemplatetype() == Template_type.CSTR_PATTERN && lastType.getTypetype() == Type.Type_type.TYPE_UCHARSTRING) {
			realBody = body.setTemplatetype(timestamp, Template_type.USTR_PATTERN);
			if (realBody instanceof UnivCharString_Pattern_Template) {
				((UnivCharString_Pattern_Template)realBody).getPatternstring().setPatterntype(PatternType.UNIVCHARSTRING_PATTERN);
			}
		}

		if (formalParList != null) {
			formalParList.reset();
			checkDefault(timestamp);
			formalParList.check(timestamp, getAssignmentType());
			if (isLocal()) {
				location.reportSemanticError(MessageFormat.format(PARAMETRIZED_LOCAL_TEMPLATE, getIdentifier()));
			}
		}

		final ITTCN3Template tempBody = type.checkThisTemplateRef(timestamp, realBody);

		checkModified(timestamp);
		checkRecursiveDerivation(timestamp);
		tempBody.checkThisTemplateGeneric(timestamp, type, derivedReference != null, true, true, true, hasImplicitOmitAttribute(timestamp), null);

		checkErroneousAttributes(timestamp);

		final IReferenceChain tempReferenceChain = ReferenceChain.getInstance(CIRCULAREMBEDDEDRECURSION, true);
		tempReferenceChain.add(this);
		tempBody.checkRecursions(timestamp, tempReferenceChain);
		tempReferenceChain.release();

		if (templateRestriction != TemplateRestriction.Restriction_type.TR_NONE) {
			generateRestrictionCheck = TemplateRestriction.check(timestamp, this, tempBody, null);
			if (formalParList != null && templateRestriction != TemplateRestriction.Restriction_type.TR_PRESENT) {
				final int nofFps = formalParList.getNofParameters();
				for (int i = 0; i < nofFps; i++) {
					final FormalParameter fp = formalParList.getParameterByIndex(i);
					// if formal par is not template then
					// skip restriction checking,
					// templates can have only `in'
					// parameters
					if (fp.getAssignmentType() != Assignment.Assignment_type.A_PAR_TEMP_IN) {
						continue;
					}
					final TemplateRestriction.Restriction_type fpTemplateRestriction = fp.getTemplateRestriction();
					switch (templateRestriction) {
					case TR_VALUE:
					case TR_OMIT:
						switch (fpTemplateRestriction) {
						case TR_VALUE:
						case TR_OMIT:
							// allowed
							break;
						case TR_PRESENT:
							fp.getLocation().reportSemanticError(
									MessageFormat.format(WITHTEMPRESTNOTALLOWED,
											fpTemplateRestriction.getDisplayName()));
							break;
						case TR_NONE:
							fp.getLocation().reportSemanticError(WITHOUTTEMPRESTNOTALLOWED);
							break;
						default:
							break;
						}
						break;
					default:
						break;
					}
				}
			}
		}

		if (formalParList != null) {
			formalParList.setGenName(getGenName());
		}
		//body.setGenNamePrefix("template_");//currently does not need the prefix
		body.setGenNameRecursive(getGenName());
		body.setCodeSection(CodeSectionType.CS_POST_INIT);
	}

	private void checkDefault(final CompilationTimeStamp timestamp) {
		if (derivedReference == null) {
			if (formalParList != null && formalParList.hasNotusedDefaultValue()) {
				formalParList.getLocation().reportSemanticError(NOBASETEMPLATEFORDASH);
			}
			return;
		}

		final Assignment assignment = derivedReference.getRefdAssignment(timestamp, false, null);
		if (assignment == null) {
			return;
		}

		if (Assignment_type.A_TEMPLATE != assignment.getAssignmentType()) {
			derivedReference.getLocation().reportSemanticError(
					MessageFormat.format(TEMPLATEREFERENCEEXPECTEDINMODIFIES, assignment.getDescription()));
			return;
		}

		final Def_Template base = (Def_Template) assignment;
		base.check(timestamp);

		final FormalParameterList baseParameters = base.getFormalParameterList(timestamp);
		final int nofBaseFps = (baseParameters == null) ? 0 : baseParameters.getNofParameters();
		final int nofLocalFps = (formalParList == null) ? 0 : formalParList.getNofParameters();
		int minFps;
		if (nofLocalFps < nofBaseFps) {
			minFps = nofLocalFps;
		} else {
			minFps = nofBaseFps;
		}

		for (int i = 0; i < minFps; i++) {
			final FormalParameter baseFp = baseParameters.getParameterByIndex(i);
			final FormalParameter localFp = formalParList.getParameterByIndex(i);
			if (localFp.hasNotusedDefaultValue()) {
				if (baseFp.hasDefaultValue()) {
					localFp.setDefaultValue(baseFp.getDefaultParameter());
				} else {
					localFp.getLocation().reportSemanticError(NOBASETEMPLATEPARFORDASH);
				}
			}
		}

		for (int i = nofBaseFps; i < nofLocalFps; i++) {
			final FormalParameter localFp = formalParList.getParameterByIndex(i);
			if (localFp.hasNotusedDefaultValue()) {
				localFp.getLocation().reportSemanticError(NOBASETEMPLATEPARFORDASH);
			}
		}
	}

	/**
	 * Checks the correctness of the modification of this template done to
	 * the modified one if it has any.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle
	 * */
	private void checkModified(final CompilationTimeStamp timestamp) {
		if (derivedReference == null) {
			return;
		}

		final Assignment assignment = derivedReference.getRefdAssignment(timestamp, false, null);
		if (assignment == null) {
			return;
		}

		if (Assignment_type.A_TEMPLATE != assignment.getAssignmentType()) {
			derivedReference.getLocation().reportSemanticError(
					MessageFormat.format(TEMPLATEREFERENCEEXPECTEDINMODIFIES, assignment.getDescription()));
			return;
		}

		baseTemplate = (Def_Template) assignment;
		baseTemplate.check(timestamp);

		final IType baseType = baseTemplate.getType(timestamp);
		if (!type.isCompatible(timestamp, baseType, null, null, null)) {
			type.getLocation().reportSemanticError(
					MessageFormat.format(IMCOMPATIBLEBASETYPE, baseTemplate.getFullName(), baseType.getFullName(),
							type.getFullName()));
		}

		final FormalParameterList baseParameters = baseTemplate.getFormalParameterList(timestamp);
		final int nofBaseFps = (baseParameters == null) ? 0 : baseParameters.getNofParameters();
		final int nofLocalFps = (formalParList == null) ? 0 : formalParList.getNofParameters();
		int minFps;
		if (nofLocalFps < nofBaseFps) {
			location.reportSemanticError(MessageFormat.format(FEWERFORMALPARAMETERS, baseTemplate.getFullName(), nofBaseFps, nofLocalFps));
			minFps = nofLocalFps;
		} else {
			minFps = nofBaseFps;
		}

		for (int i = 0; i < minFps; i++) {
			final FormalParameter baseFormalpar = baseParameters.getParameterByIndex(i);
			final FormalParameter localFormalpar = formalParList.getParameterByIndex(i);

			if (!baseFormalpar.getAssignmentType().semanticallyEquals(localFormalpar.getAssignmentType())) {
				localFormalpar.getLocation().reportSemanticError(
						MessageFormat.format(DIFFERENTPARAMETERKINDS, baseTemplate.getFullName(),
								baseFormalpar.getAssignmentName(), localFormalpar.getAssignmentName()));
			}

			final Type baseFpType = baseFormalpar.getType(timestamp);
			final Type localFpType = localFormalpar.getType(timestamp);
			if (!baseFpType.isCompatible(timestamp, localFpType, null, null, null)) {
				if (!localFpType.getIsErroneous(timestamp) && !baseFpType.getIsErroneous(timestamp)) {
					localFpType.getLocation().reportSemanticError(
							MessageFormat.format(INCOMPATIBLEBASEPARAMETERTYPE, baseTemplate.getFullName(),
									baseFpType.getTypename(), localFpType.getTypename()));
				}
			}

			final Identifier baseFormalparId = baseFormalpar.getIdentifier();
			final Identifier localFormalparId = localFormalpar.getIdentifier();
			if (!baseFormalparId.equals(localFormalparId)) {
				localFormalpar.getLocation().reportSemanticError(
						MessageFormat.format(DIFFERENTPARAMETERNAMES, baseTemplate.getFullName(),
								baseFormalparId.getDisplayName(), localFormalparId.getDisplayName()));
			}
		}

		body.setBaseTemplate(baseTemplate.getTemplate(timestamp));
	}

	/**
	 * Checks if the derivation chain of the templates is recursive or not.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle
	 * */
	private void checkRecursiveDerivation(final CompilationTimeStamp timestamp) {
		if (recursiveDerivationChecked != null && !recursiveDerivationChecked.isLess(timestamp)) {
			return;
		}

		if (baseTemplate != null) {
			final IReferenceChain tempReferenceChain = ReferenceChain.getInstance(CIRCULARBASETEMPLATES, true);

			tempReferenceChain.add(this);
			Def_Template iterator = baseTemplate;
			while (iterator != null) {
				if (iterator.recursiveDerivationChecked != null && !iterator.recursiveDerivationChecked.isLess(timestamp)) {
					break;
				}

				if (tempReferenceChain.add(iterator)) {
					iterator.recursiveDerivationChecked = timestamp;
				} else {
					break;
				}

				iterator = iterator.baseTemplate;
			}

			tempReferenceChain.release();
		}

		recursiveDerivationChecked = timestamp;
	}

	@Override
	/** {@inheritDoc} */
	public void postCheck() {
		super.postCheck();
		postCheckPrivateness();
	}

	@Override
	/** {@inheritDoc} */
	public String getProposalKind() {
		final StringBuilder builder = new StringBuilder();
		if (type != null) {
			type.getProposalDescription(builder);
		}
		builder.append(KIND);
		return builder.toString();
	}

	@Override
	/** {@inheritDoc} */
	public String getProposalDescription() {
		final StringBuilder nameBuilder = new StringBuilder(identifier.getDisplayName());
		if (formalParList != null) {
			nameBuilder.append('(');
			formalParList.getAsProposalDesriptionPart(nameBuilder);
			nameBuilder.append(')');
		}
		return nameBuilder.toString();

	}

	@Override
	/** {@inheritDoc} */
	public void addProposal(final ProposalCollector propCollector, final int index) {
		final List<ISubReference> subrefs = propCollector.getReference().getSubreferences();
		if (subrefs.size() <= index) {
			return;
		}

		if (subrefs.size() == index + 1 && identifier.getName().toLowerCase().startsWith(subrefs.get(index).getId().getName().toLowerCase())) {
			if (formalParList != null) {
				final StringBuilder patternBuilder = new StringBuilder(identifier.getDisplayName());
				patternBuilder.append('(');
				formalParList.getAsProposalPart(patternBuilder);
				patternBuilder.append(')');
				propCollector.addTemplateProposal(identifier.getDisplayName(), new Template(getProposalDescription(), "",
						propCollector.getContextIdentifier(), patternBuilder.toString(), false),
						TTCN3CodeSkeletons.SKELETON_IMAGE);
			}
			super.addProposal(propCollector, index);
		} else if (subrefs.size() > index + 1 && type != null && identifier.getName().equals(subrefs.get(index).getId().getName())) {
			// perfect match
			type.addProposal(propCollector, index + 1);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void addDeclaration(final DeclarationCollector declarationCollector, final int index) {
		final List<ISubReference> subrefs = declarationCollector.getReference().getSubreferences();
		if (subrefs.size() > index && identifier.getName().equals(subrefs.get(index).getId().getName())) {
			if (subrefs.size() > index + 1 && type != null) {
				type.addDeclaration(declarationCollector, index + 1);
			} else if (subrefs.size() == index + 1) {
				declarationCollector.addDeclaration(this);
			}
		}
	}

	@Override
	/** {@inheritDoc} */
	public TemplateRestriction.Restriction_type getTemplateRestriction() {
		return templateRestriction;
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			lastTimeChecked = null;
			boolean enveloped = false;

			final Location temporalIdentifier = identifier.getLocation();
			if (reparser.envelopsDamage(temporalIdentifier) || reparser.isExtending(temporalIdentifier)) {
				reparser.extendDamagedRegion(temporalIdentifier);
				final IIdentifierReparser r = new IdentifierReparser(reparser);
				final int result = r.parseAndSetNameChanged();
				identifier = r.getIdentifier();
				// damage handled
				if (result == 0 && identifier != null) {
					enveloped = true;
				} else {
					removeBridge();
					throw new ReParseException(result);
				}
			}

			if (type != null) {
				if (enveloped) {
					type.updateSyntax(reparser, false);
					reparser.updateLocation(type.getLocation());
				} else if (reparser.envelopsDamage(type.getLocation())) {
					try {
						type.updateSyntax(reparser, true);
						enveloped = true;
						reparser.updateLocation(type.getLocation());
					} catch (ReParseException e) {
						removeBridge();
						throw e;
					}
				}
			}

			if (formalParList != null) {
				if (enveloped) {
					formalParList.updateSyntax(reparser, false);
					reparser.updateLocation(formalParList.getLocation());
				} else if (reparser.envelopsDamage(formalParList.getLocation())) {
					try {
						formalParList.updateSyntax(reparser, true);
						enveloped = true;
						reparser.updateLocation(formalParList.getLocation());
					} catch (ReParseException e) {
						removeBridge();
						throw e;
					}
				}
			}

			if (derivedReference != null) {
				if (enveloped) {
					derivedReference.updateSyntax(reparser, false);
					reparser.updateLocation(derivedReference.getLocation());
				} else if (reparser.envelopsDamage(derivedReference.getLocation())) {
					try {
						derivedReference.updateSyntax(reparser, true);
						enveloped = true;
						reparser.updateLocation(derivedReference.getLocation());
					} catch (ReParseException e) {
						removeBridge();
						throw e;
					}
				}
			}

			if (body != null) {
				if (enveloped) {
					body.updateSyntax(reparser, false);
					reparser.updateLocation(body.getLocation());
				} else if (reparser.envelopsDamage(body.getLocation())) {
					try {
						body.updateSyntax(reparser, true);
						enveloped = true;
						reparser.updateLocation(body.getLocation());
					} catch (ReParseException e) {
						removeBridge();
						throw e;
					}
				}
			}

			if (withAttributesPath != null) {
				if (enveloped) {
					withAttributesPath.updateSyntax(reparser, false);
					reparser.updateLocation(withAttributesPath.getLocation());
				} else if (reparser.envelopsDamage(withAttributesPath.getLocation())) {
					try {
						withAttributesPath.updateSyntax(reparser, true);
						enveloped = true;
						reparser.updateLocation(withAttributesPath.getLocation());
					} catch (ReParseException e) {
						removeBridge();
						throw e;
					}
				}
			}

			if (!enveloped) {
				removeBridge();
				throw new ReParseException();
			}

			return;
		}

		reparser.updateLocation(identifier.getLocation());

		if (type != null) {
			type.updateSyntax(reparser, false);
			reparser.updateLocation(type.getLocation());
		}

		if (formalParList != null) {
			formalParList.updateSyntax(reparser, false);
			reparser.updateLocation(formalParList.getLocation());
		}

		if (derivedReference != null) {
			derivedReference.updateSyntax(reparser, false);
			reparser.updateLocation(derivedReference.getLocation());
		}

		if (body != null) {
			body.updateSyntax(reparser, false);
			reparser.updateLocation(body.getLocation());
		}

		if (withAttributesPath != null) {
			withAttributesPath.updateSyntax(reparser, false);
			reparser.updateLocation(withAttributesPath.getLocation());
		}
	}

	/**
	 * Removes the name bridging scope.
	 * */
	private void removeBridge() {
		if (bridgeScope != null) {
			bridgeScope.remove();
			bridgeScope = null;
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		super.findReferences(referenceFinder, foundIdentifiers);
		if (type != null) {
			type.findReferences(referenceFinder, foundIdentifiers);
		}
		if (formalParList != null) {
			formalParList.findReferences(referenceFinder, foundIdentifiers);
		}
		if (derivedReference != null) {
			derivedReference.findReferences(referenceFinder, foundIdentifiers);
		}
		if (body != null) {
			body.findReferences(referenceFinder, foundIdentifiers);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (!super.memberAccept(v)) {
			return false;
		}
		if (type != null && !type.accept(v)) {
			return false;
		}
		if (formalParList != null && !formalParList.accept(v)) {
			return false;
		}
		if (derivedReference != null && !derivedReference.accept(v)) {
			return false;
		}
		if (body != null && !body.accept(v)) {
			return false;
		}
		return true;
	}

	/**
	 * @return how this template should be evaluated.
	 */
	public parameterEvaluationType get_eval_type() {
		return evaluationType;
	}

	@Override
	/** {@inheritDoc} */
	public void generateCode( final JavaGenData aData, final boolean cleanUp ) {
		final String genName = getGenName();
		final StringBuilder source = new StringBuilder();

		if ( !isLocal() ) {
			if(VisibilityModifier.Private.equals(getVisibilityModifier())) {
				source.append( "\tprivate" );
			} else {
				source.append( "\tpublic" );
			}
			source.append( " static " );
		}

		final String typeName = type.getGenNameTemplate( aData, source );
		if (formalParList == null) {
			if (type.getTypetype().equals(Type_type.TYPE_ARRAY)) {
				final Array_Type arrayType = (Array_Type) type;
				final StringBuilder sbforTemp = aData.getCodeForType(arrayType.getGenNameOwn());
				arrayType.generateCodeValue(aData, sbforTemp);
				arrayType.generateCodeTemplate(aData, sbforTemp);
			}
			source.append(MessageFormat.format("{0} {1} = new {0}();\n", typeName, genName));

			if (baseTemplate != null) {
				//modified template
				if (baseTemplate.myScope.getModuleScopeGen() == myScope.getModuleScopeGen()) {
					// if the base template is in the same module its body has to be
					// initialized first
					getLocation().update_location_object(aData, aData.getPostInit());
					baseTemplate.body.generateCodeInit(aData, aData.getPostInit(), body.get_lhs_name());
				}
			}

			if ( body != null ) {
				getLocation().update_location_object(aData, aData.getPostInit());
				body.generateCodeInit( aData, aData.getPostInit(), body.get_lhs_name() );

				if (templateRestriction != Restriction_type.TR_NONE && generateRestrictionCheck) {
					TemplateRestriction.generateRestrictionCheckCode(aData, aData.getPostInit(), location, genName, templateRestriction);
				}
			}

			aData.addGlobalVariable(genName, source.toString());
		} else {
			final StringBuilder sb = aData.getSrc();
			final StringBuilder formalParameters = formalParList.generateCode(aData);
			source.append(MessageFormat.format("{0} {1}({2}) '{'\n", typeName, genName, formalParameters));
			getLocation().create_location_object(aData, source, "TEMPLATE", getIdentifier().getDisplayName());
			if (aData.getAddSourceInfo()) {
				source.append( "\t\ttry {\n" );
			}
			if (baseTemplate == null) {
				if (type.getTypetype().equals(Type_type.TYPE_ARRAY)) {
					final Array_Type arrayType = (Array_Type) type;
					final StringBuilder sbforTemp = aData.getCodeForType(arrayType.getGenNameOwn());
					arrayType.generateCodeValue(aData, sbforTemp);
					arrayType.generateCodeTemplate(aData, sbforTemp);
				}
				source.append(MessageFormat.format("final {0} ret_val = new {0}();\n", typeName));
			} else {
				//modified template
				source.append(MessageFormat.format("final {0} ret_val = new {0}({1}", typeName, baseTemplate.getGenNameFromScope(aData, source, "")));
				if (baseTemplate.formalParList != null) {
					//the base is also parameterized
					source.append('(');
					for (int i = 0; i < baseTemplate.formalParList.getNofParameters(); i++) {
						if (i > 0) {
							source.append(", ");
						}
						source.append(formalParList.getParameterByIndex(i).getIdentifier().getName());
					}
					source.append(')');
				}
				source.append(");\n");
			}
			if ( body != null ) {
				body.generateCodeInit( aData, source, "ret_val" );

				if (templateRestriction != Restriction_type.TR_NONE && generateRestrictionCheck) {
					TemplateRestriction.generateRestrictionCheckCode(aData, source, location, "ret_val", templateRestriction);
				}
			}

			source.append("return ret_val;\n");
			if (aData.getAddSourceInfo()) {
				source.append( "\t\t} finally {\n" );
				getLocation().release_location_object(aData, source);
				source.append( "\t\t}\n" );
			}
			source.append("}\n\n");

			sb.append(source);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void generateCodeString(final JavaGenData aData, final StringBuilder source) {
		final String genName = getGenName();

		if (formalParList == null) {
			final String typeName = type.getGenNameTemplate( aData, source );

			if (baseTemplate == null) {
				if (type.getTypetype().equals(Type_type.TYPE_ARRAY)) {
					final Array_Type arrayType = (Array_Type) type;
					final StringBuilder sb = aData.getCodeForType(arrayType.getGenNameOwn());
					arrayType.generateCodeValue(aData, sb);
					arrayType.generateCodeTemplate(aData, sb);
				}

				if (body != null && body.hasSingleExpression()) {
					source.append(MessageFormat.format("{0} {1} = new {0}({2});\n", typeName, genName, body.getSingleExpression(aData, false)));
				} else {
					source.append(MessageFormat.format("{0} {1} = new {0}();\n", typeName, genName));
					if ( body != null ) {
						body.generateCodeInit( aData, source, genName );
					}
				}
			} else {
				source.append(MessageFormat.format("{0} {1} = new {0}({2});\n", typeName, genName, baseTemplate.getGenNameFromScope(aData, source, "")));
				if ( body != null ) {
					body.generateCodeInit( aData, source, genName );
				}
			}

		} else {
			source.append(MessageFormat.format("Code generation for parameterized local template `{0}' is not supported", identifier.getDisplayName()));
		}

		if (templateRestriction != Restriction_type.TR_NONE && generateRestrictionCheck) {
			TemplateRestriction.generateRestrictionCheckCode(aData, source, location, genName, templateRestriction);
		}
	}
}
