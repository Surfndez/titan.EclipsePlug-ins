/******************************************************************************
 * Copyright (c) 2000-2019 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.editors.ttcn3editor;

import java.util.List;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.titan.designer.AST.IOutlineElement;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.graphics.ImageCache;

/**
 * @author Kristof Szabados
 * */
public final class OutlineLabelProvider extends LabelProvider {
	@Override
	public Image getImage(final Object element) {
		String iconName = "titan.gif";
		if (element instanceof IOutlineElement) {
			final IOutlineElement e = (IOutlineElement) element;
			iconName = e.getOutlineIcon();
		} else if (element instanceof List<?>) {
			iconName = "imports.gif";
		}

		return ImageCache.getImage(iconName);
	}

	@Override
	public String getText(final Object element) {
		Identifier identifier = null;
		if (element instanceof IOutlineElement) {
			final IOutlineElement e = (IOutlineElement) element;
			final String outlineText = e.getOutlineText();
			if (outlineText.length() != 0) {
				return outlineText;
			}
			identifier = e.getIdentifier();
		} else if (element instanceof List<?>) {
			return "imports";
		}

		if (identifier == null) {
			return "unknown";
		}

		return identifier.getDisplayName();
	}
}
