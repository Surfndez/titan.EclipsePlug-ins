/******************************************************************************
 * Copyright (c) 2000-2019 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.runtime.core;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.titan.runtime.core.Param_Types.Module_Param_Name;
import org.eclipse.titan.runtime.core.Param_Types.Module_Parameter;
import org.eclipse.titan.runtime.core.Param_Types.Module_Parameter.basic_check_bits_t;
import org.eclipse.titan.runtime.core.Param_Types.Module_Parameter.type_t;
import org.eclipse.titan.runtime.core.RecordOf_Match.answer;
import org.eclipse.titan.runtime.core.RecordOf_Match.match_function_t;
import org.eclipse.titan.runtime.core.RecordOf_Match.type_of_matching;

/**
 * @author Farkas Izabella Ingrid
 * @author Andrea Palfi
 *
 * TODO recursive_permutation_match might not need to be here
 *
 */
public class TitanTemplate_Array<Tvalue extends Base_Type,Ttemplate extends Base_Template> extends Restricted_Length_Template {

	//template parameters
	private Class<Tvalue> classValue;
	private Class<Ttemplate> classTemplate;
	protected int array_size;
	private int indexOffset;

	//single value
	protected Base_Template[] single_value;
	protected int singleSize;

	//value array
	protected TitanTemplate_Array<?,?>[] value_list;
	protected int listSize;

	/**
	 * permutation interval
	 */
	class Pair_of_elements {
		//beginning and ending index
		private final int start_index;
		private final int end_index;

		public Pair_of_elements(final int start_index, final int end_index) {
			this.start_index = start_index;
			this.end_index = end_index;
		}
	}

	private ArrayList<Pair_of_elements> permutationIntervals;

	//private part
	private void encode_text_permutation(final Text_Buf text_buf) {
		encode_text_restricted(text_buf);

		final int number_of_permutations = get_number_of_permutations();
		text_buf.push_int(number_of_permutations);

		for (int i = 0; i < number_of_permutations; i++) {
			text_buf.push_int(permutationIntervals.get(i).start_index);
			text_buf.push_int(permutationIntervals.get(i).end_index);
		}
	}

	private void decode_text_permutation(final Text_Buf text_buf) {
		decode_text_restricted(text_buf);

		final int number_of_permutations = text_buf.pull_int().get_int();
		permutationIntervals = new ArrayList<Pair_of_elements>(number_of_permutations);

		for (int i = 0; i < number_of_permutations; i++) {
			final int start_index = text_buf.pull_int().get_int();
			final int end_index = text_buf.pull_int().get_int();
			permutationIntervals.add(new Pair_of_elements(start_index, end_index));
		}
	}

	private void clean_up_intervals() {
		//numberOfperm = 0;
		permutationIntervals = null;
	}

	@Override
	protected void set_selection(final template_sel otherValue) {
		super.set_selection(otherValue);
		clean_up_intervals();
	}

	protected void set_selection(final TitanTemplate_Array<Tvalue, Ttemplate> otherValue) {
		super.set_selection(otherValue);
		clean_up_intervals();

		if (otherValue.template_selection == template_sel.SPECIFIC_VALUE && otherValue.permutationIntervals != null) {
			permutationIntervals = new ArrayList<Pair_of_elements>(otherValue.permutationIntervals.size());
			permutationIntervals.addAll(otherValue.permutationIntervals);
		}
	}

	public final List<Pair_of_elements> copy_permutations(final List<Pair_of_elements> srcList) {
		if (srcList == null) {
			return null;
		}

		final List<Pair_of_elements> newList = new ArrayList<Pair_of_elements>(srcList.size());
		for (final Pair_of_elements srcElem : srcList) {
			final Pair_of_elements newElem = new Pair_of_elements(srcElem.start_index, srcElem.start_index);
			newList.add(newElem);
		}
		return newList;
	}

	public void remove_all_permutations() {
		clean_up_intervals();
	}

	public void add_permutation(final int start_index, final int end_index) {
		if (start_index > end_index) {
			throw new TtcnError("wrong permutation interval settings start " + start_index + " can not be greater than end " + end_index);
		}

		final int number_of_permutations = get_number_of_permutations();
		if (number_of_permutations > 0 && permutationIntervals.get(number_of_permutations - 1).end_index >= start_index) {
			throw new TtcnError(MessageFormat.format("the {0}th permutation overlaps the previous one", number_of_permutations));
		}

		if (permutationIntervals == null) {
			permutationIntervals = new ArrayList<Pair_of_elements>(1);
		}

		final Pair_of_elements newElem = new Pair_of_elements(start_index, end_index);
		permutationIntervals.add(newElem);
	}

	public int get_number_of_permutations() {
		return permutationIntervals != null ? permutationIntervals.size() : 0;
	}

	public int get_permutation_start(final int index_value) {
		if (index_value >= get_number_of_permutations()) {
			throw new TtcnError(MessageFormat.format("Index overflow ({0})", index_value));
		}

		return permutationIntervals.get(index_value).start_index;
	}

	public int get_permutation_end(final int index_value) {
		if (index_value >= get_number_of_permutations()) {
			throw new TtcnError(MessageFormat.format("Index overflow ({0})", index_value));
		}

		return permutationIntervals.get(index_value).end_index;
	}

	public int get_permutation_size(final int index_value) {
		if (index_value >= get_number_of_permutations()) {
			throw new TtcnError(MessageFormat.format("Index overflow ({0})", index_value));
		}

		return permutationIntervals.get(index_value).end_index - permutationIntervals.get(index_value).start_index + 1;
	}

	boolean permutation_starts_at(final int index_value) {
		final int number_of_permutations = get_number_of_permutations();
		for (int i = 0; i < number_of_permutations; i++) {
			if (permutationIntervals.get(i).start_index == index_value) {
				return true;
			}
		}

		return false;
	}

	boolean permutation_ends_at(final int index_value) {
		final int number_of_permutations = get_number_of_permutations();
		for (int i = 0; i < number_of_permutations; i++) {
			if (permutationIntervals.get(i).end_index == index_value) {
				return true;
			}
		}

		return false;
	}

	protected void copy_value(final TitanValue_Array<Tvalue> otherValue) {
		array_size = otherValue.array_size;
		indexOffset = otherValue.indexOffset;
		singleSize = otherValue.array_size;
		single_value = new Base_Template[singleSize];

		for (int i = 0; i < singleSize; ++i) {
			try {
				final Ttemplate helper = classTemplate.newInstance();
				helper.operator_assign(otherValue.get_at(i));
				single_value[i] = helper;
			} catch (InstantiationException e) {
				throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
			} catch (IllegalAccessException e) {
				throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
			}
		}

		set_selection(template_sel.SPECIFIC_VALUE);
	}

	private void copy_template(final TitanTemplate_Array<Tvalue, Ttemplate> otherValue) {
		switch (otherValue.template_selection)
		{
		case SPECIFIC_VALUE:
			array_size = otherValue.array_size;
			indexOffset = otherValue.indexOffset;
			singleSize = otherValue.singleSize;
			single_value = new Base_Template[singleSize];

			for (int i = 0; i < singleSize; ++i) {
				try {
					final Ttemplate helper = classTemplate.newInstance();
					helper.operator_assign(otherValue.single_value[i]);
					single_value[i] = helper;
				} catch (InstantiationException e) {
					throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
				} catch (IllegalAccessException e) {
					throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
				}
			}
			break;
		case OMIT_VALUE:
		case ANY_VALUE:
		case ANY_OR_OMIT:
			break;
		case VALUE_LIST:
		case COMPLEMENTED_LIST:
			array_size = otherValue.array_size;
			indexOffset = otherValue.indexOffset;
			listSize = otherValue.listSize;
			value_list = new TitanTemplate_Array[listSize];

			for (int i = 0; i < listSize; ++i) {
				final TitanTemplate_Array<Tvalue, Ttemplate> temp = new TitanTemplate_Array<Tvalue, Ttemplate>((TitanTemplate_Array<Tvalue, Ttemplate>)otherValue.value_list[i]);
				value_list[i] = temp;
			}
			break;
		default:
			throw new TtcnError("Copying an uninitialized/unsupported array template.");
		}
		set_selection(otherValue);
	}

	private TitanTemplate_Array(final Class<Tvalue> classValue, final Class<Ttemplate> classTemplate) {
		this.classValue = classValue;
		this.classTemplate = classTemplate;
	}

	public TitanTemplate_Array(final Class<Tvalue> classValue, final Class<Ttemplate> classTemplate, final template_sel otherValue) {
		super(otherValue);
		check_single_selection(otherValue);

		this.classValue = classValue;
		this.classTemplate = classTemplate;
	}

	// originally TEMPLATE_ARRAY(null_type other_value);
	public TitanTemplate_Array(final Class<Tvalue> classValue, final Class<Ttemplate> classTemplate, final TitanNull_Type otherValue) {
		super(template_sel.SPECIFIC_VALUE);
		this.classValue = classValue;
		this.classTemplate = classTemplate;

		single_value = null;
		permutationIntervals = null;
	}

	public TitanTemplate_Array(final Class<Ttemplate> classTemplate, final TitanValue_Array<Tvalue> otherValue) {
		this.classValue = otherValue.clazz;
		this.classTemplate = classTemplate;

		copy_value(otherValue);
	}

	public TitanTemplate_Array(final TitanTemplate_Array<Tvalue, Ttemplate> otherValue) {
		this.classValue = otherValue.classValue;
		this.classTemplate = otherValue.classTemplate;

		copy_template(otherValue);
	}

	public TitanTemplate_Array(final Class<Tvalue> classValue, final Class<Ttemplate> classTemplate, final int size, final int offset) {
		this.classValue = classValue;
		this.classTemplate = classTemplate;

		indexOffset = offset;
		set_size(size);
	}

	public TitanTemplate_Array(final Optional<TitanValue_Array<Tvalue>> other_value) {
		clean_up();
		switch (other_value.get_selection()) {
		case OPTIONAL_PRESENT:
			copy_value(other_value.get());
			break;
		case OPTIONAL_OMIT:
			set_selection(template_sel.OMIT_VALUE);
			break;
		default:
			throw new TtcnError("Creating an array template from an unbound optional field.");
		}
	}

	//FIXME: actualization
	public void set_size(final int length) {
		if (length < 0) {
			throw new TtcnError("Internal error: Setting a negative size for an array template.");
		}
		final template_sel old_selection = template_selection;

		if (old_selection != template_sel.SPECIFIC_VALUE) {
			clean_up();
			set_selection(template_sel.SPECIFIC_VALUE);
			single_value = null;
		}

		if (length > singleSize) {
			//FIXME Potentionally erroneous behaviour
			single_value = new Base_Template[length];
			if (old_selection == template_sel.ANY_VALUE || old_selection == template_sel.ANY_OR_OMIT) {

				for (int i = singleSize; i < length; ++i) {
					try {
						final Ttemplate helper = classTemplate.newInstance();
						helper.set_selection(template_sel.ANY_VALUE);
						single_value[i] = helper;
					} catch (InstantiationException e) {
						throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
					} catch (IllegalAccessException e) {
						throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
					}
				}
			} else {
				for (int i = singleSize; i < length; ++i) {
					try {
						final Ttemplate helper = classTemplate.newInstance();
						single_value[i] = helper;
					} catch (InstantiationException e) {
						throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
					} catch (IllegalAccessException e) {
						throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
					}
				}
			}
			// single_value.n_elements = length;
		} // else if (length < singleSize) {
		//	for (int i = singleSize-1; i >= length; --i) {
		//		single_value.remove(i);
		//	}
		//}

		array_size = length;
		singleSize = length;
	}

	public void setOffset(final int offset) {
		indexOffset = offset;
	}

	@Override
	public void clean_up() {
		switch (template_selection) {
		case SPECIFIC_VALUE:
			singleSize = 0;
			//single_value.clear();
			single_value = null;
			break;
		case VALUE_LIST:
		case COMPLEMENTED_LIST:
			listSize = 0;
			for (int i = 0; i < listSize; ++i) {
				value_list[i].clean_up();
			}
			value_list = null;
			break;
		default:
			break;
		}
		template_selection = template_sel.UNINITIALIZED_TEMPLATE;
	}

	@Override
	public TitanTemplate_Array<Tvalue, Ttemplate> operator_assign(final template_sel otherValue) {
		check_single_selection(otherValue);
		clean_up();
		set_selection(otherValue);
		return this;
	}

	/**
	 * Cleans up this template and sets it as a specific value template.
	 *<p>
	 * operator= in the core.
	 *
	 * @param otherValue
	 *                the other value to assign.
	 * @return the new template object.
	 */
	public TitanTemplate_Array<Tvalue, Ttemplate> operator_assign(final TitanNull_Type otherValue) {
		clean_up();
		set_selection(template_sel.SPECIFIC_VALUE);
		return this;
	}

	/**
	 * Assigns the other value to this template.
	 * Overwriting the current content in the process.
	 * Uses deep copy.
	 *<p>
	 * operator= in the core.
	 *
	 * @param otherValue
	 *                the other value to assign.
	 * @return the new template object.
	 */
	public TitanTemplate_Array<Tvalue, Ttemplate> operator_assign(final TitanValue_Array<Tvalue> otherValue) {
		clean_up();
		copy_value(otherValue);
		return this;
	}

	/**
	 * Assigns the other value to this template.
	 * Overwriting the current content in the process.
	 * Uses deep copy.
	 *<p>
	 * operator= in the core.
	 *
	 * @param otherValue
	 *                the other value to assign.
	 * @return the new template object.
	 */
	public TitanTemplate_Array<Tvalue, Ttemplate> operator_assign(final Optional<TitanValue_Array<Tvalue>> other_value) {
		clean_up();
		switch (other_value.get_selection()) {
		case OPTIONAL_PRESENT:
			copy_value(other_value.get());
			break;
		case OPTIONAL_OMIT:
			set_selection(template_sel.OMIT_VALUE);
			break;
		default:
			throw new TtcnError("Assignment of an unbound optional field to an array template.");
		}
		return this;
	}

	/**
	 * Assigns the other template to this template.
	 * Overwriting the current content in the process.
	 * Uses depp copy.
	 *<p>
	 * operator= in the core.
	 *
	 * @param otherValue
	 *                the other value to assign.
	 * @return the new template object.
	 */
	public TitanTemplate_Array<Tvalue, Ttemplate> operator_assign(final TitanTemplate_Array<Tvalue,Ttemplate> otherValue) {
		if (otherValue != this) {
			clean_up();
			copy_template(otherValue);
		}

		return this;
	}

	/**
	 * Gives access to the given element. Indexing begins from zero.
	 *
	 * Index underflow and overflow causes dynamic test case error. Also if
	 * the template is not a specific value template it becomes one.
	 *
	 * operator[] in the core.
	 *
	 * @param index
	 *                the index of the element to return.
	 * @return the element at the specified position in this list
	 * */
	@SuppressWarnings("unchecked")
	public Ttemplate get_at(int index) {
		if (index < indexOffset || index >= indexOffset + array_size) {
			throw new TtcnError(MessageFormat.format("Accessing an element of an array template using invalid index: {0}. "
					+ "Index range is [{1},{2}].",
					index, indexOffset, indexOffset + (int)array_size));
		}
		index -= indexOffset;
		switch (template_selection) {
		case SPECIFIC_VALUE:
			if (index >= singleSize) {
				set_size(index + 1);
			}
			break;
		case ANY_VALUE:
		case ANY_OR_OMIT:
			set_size(listSize);
			break;
		default:
			set_size(index + 1);
			break;
		}
		return (Ttemplate)single_value[index];
	}

	/**
	 * Gives access to the given element. Indexing begins from zero.
	 *
	 * Index underflow and overflow causes dynamic test case error. Also if
	 * the template is not a specific value template it becomes one.
	 *
	 * operator[] in the core.
	 *
	 * @param index
	 *                the index of the element to return.
	 * @return the element at the specified position in this list
	 * */
	public Ttemplate get_at(final TitanInteger index) {
		index.must_bound("Using an unbound integer value for indexing an array template.");

		return get_at(index.get_int());
	}

	/**
	 * Gives read-only access to the given element.
	 *
	 * Index underflow and overflow causes dynamic test case error.
	 * Also if the template is not a specific value template
	 *
	 * const operator[] const in the core.
	 *
	 * @param index
	 *            the index of the element to return.
	 * @return the element at the specified position in this list
	 * */
	@SuppressWarnings("unchecked")
	public Ttemplate constGet_at(int index) {
		if (index < indexOffset) {
			throw new TtcnError(MessageFormat.format("Accessing an element of an array template using invalid index: {0}. "
					+ "Index range is [{1},{2}].", index, indexOffset, indexOffset + (int) array_size));
		}

		index -= indexOffset;
		if (template_selection != template_sel.SPECIFIC_VALUE) {
			throw new TtcnError("Accessing an element of a non-specific array template.");
		}
		if (index >= singleSize) {
			throw new TtcnError(MessageFormat.format("Index overflow in an array template: The index is {0} (starting at {1}),"
					+ " but the template has only {2} elements.", index + indexOffset, indexOffset, singleSize));
		}

		return (Ttemplate)single_value[index];
	}

	/**
	 * Gives read-only access to the given element.
	 *
	 * Index underflow and overflow causes dynamic test case error.
	 * Also if the template is not a specific value template
	 *
	 * const operator[] const in the core.
	 *
	 * @param index
	 *            the index of the element to return.
	 * @return the element at the specified position in this list
	 * */
	public Ttemplate constGet_at(final TitanInteger index) {
		index.must_bound("Using an unbound integer value for indexing an array template.");

		return constGet_at(index.get_int());
	}

	/**
	 * Returns the number of elements, that is, the size of the array.
	 *
	 * Results in dynamic testcase error if the template is not a specific
	 * value or value list kind.
	 *
	 * n_elem in the core.
	 *
	 * @return the number of elements.
	 * */
	public int n_elem() {
		switch (template_selection) {
		case SPECIFIC_VALUE:
			return singleSize;
		case VALUE_LIST:
			return listSize;
		default:
			throw new TtcnError("Performing n_elem");
		}
	}

	/**
	 * Returns the number of elements, that is, the largest used index plus
	 * one and zero for the empty value.
	 *
	 * lengthof in the core
	 *
	 * @return the number of elements.
	 * */
	public TitanInteger lengthof() {
		return size_of(false);
	}

	public TitanInteger size_of(){
		return size_of(true);
	}

	public TitanInteger size_of(final boolean isSize) {
		final String opName = isSize ? "size" : "length";
		int minSize = 0;
		boolean has_any_or_none = false;

		if (is_ifPresent) {
			throw new TtcnError("Performing " + opName + "of() operation on an array template which has an ifpresent attribute.");
		}

		switch (template_selection) {
		case SPECIFIC_VALUE:
			minSize = 0;
			has_any_or_none = false;
			int count = singleSize;
			if (! isSize) { //lengthof()
				while (count > 0 && !single_value[count - 1].is_bound()) {
					count -= 1;
				}
			}

			for (int i = 0; i < count; ++i) {
				switch (single_value[i].get_selection()) {
				case OMIT_VALUE:
					throw new TtcnError("Performing" + opName + "of() operation on an array template containing omit element.");
				case ANY_OR_OMIT:
					has_any_or_none = true;
					break;
				default:
					minSize += 1;
					break;
				}
			}
			break;
		case OMIT_VALUE:
			throw new TtcnError("Performing " + opName + "of() operation on an array template containing omit element.");
		case ANY_VALUE:
		case ANY_OR_OMIT:
			minSize = 0;
			has_any_or_none = true;
			break;
		case VALUE_LIST:
			if (listSize != 0) {
				throw new TtcnError("Performing " + opName + "of() operation on an array template containing an empty list.");
			}
			final int itemSize = value_list[0].size_of(isSize).get_int();
			for (int i = 1; i < listSize; ++i) {
				if (value_list[i].size_of(isSize).get_int() != itemSize) {
					throw new TtcnError("Performing " + opName + "of() operation on an array template containing a value list with different sizes.");
				}
			}
			minSize = itemSize;
			has_any_or_none = false;
			break;
		case COMPLEMENTED_LIST:
			throw new TtcnError("Performing " + opName + "of() operation on an array template containing complemented list.");
		default:
			throw new TtcnError("Performing " + opName + "of() operation on an uninitialized/unsupported array template.");
		}

		return new TitanInteger(check_section_is_single(minSize, has_any_or_none, opName, "an", "array template"));
	}

	@Override
	public boolean is_value() {
		if (template_selection != template_sel.SPECIFIC_VALUE || is_ifPresent) {
			return false;
		}

		for (int i = 0; i < singleSize; ++i) {
			if (! single_value[i].is_value()) {
				return false;
			}
		}

		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public TitanValue_Array<Tvalue> valueof() {
		if (template_selection != template_sel.SPECIFIC_VALUE || is_ifPresent) {
			throw new TtcnError("Performing a valueof or send operation on a non-specific array template.");
		}
		// the size of the template must be the size of the value
		if (singleSize != array_size) {
			throw new TtcnError("Performing a valueof or send operation on a specific array template with invalid size.");
		}
		final TitanValue_Array<Tvalue> result = new TitanValue_Array<Tvalue>(classValue, array_size, indexOffset);
		for (int i = 0; i < array_size; ++i) {
			result.array_elements[i] = (Tvalue)single_value[i].valueof();
		}
//		result.array_size = array_size;
//		result.setOffset(indexOffset);
		return result;
	}

	@Override
	public void set_type(final template_sel templateType, final int length) {
		clean_up();
		switch (templateType) {
		case VALUE_LIST:
		case COMPLEMENTED_LIST:
			listSize = length;
			value_list = new TitanTemplate_Array[listSize];
			for (int i = 0; i < length; ++i) {
				value_list[i] = new TitanTemplate_Array<Tvalue,Ttemplate>(classValue, classTemplate);
			}

			break;
		default:
			throw new TtcnError("Internal error: Setting an invalid type for an array template.");
		}
		set_selection(templateType);
	}

	@SuppressWarnings("unchecked")
	@Override
	public TitanTemplate_Array<Tvalue, Ttemplate> list_item(final int index) {
		if (template_selection != template_sel.VALUE_LIST &&
				template_selection != template_sel.COMPLEMENTED_LIST) {
			throw new TtcnError("Internal error: Accessing a list element of a non-list array template.");
		}
		if (index >= listSize) {
			throw new TtcnError("Internal error: Index overflow in a value list array template.");
		}
		if (index < 0) {
			throw new TtcnError("Internal error: Index overflow in a value list array template.");
		}

		return (TitanTemplate_Array<Tvalue, Ttemplate>)value_list[index];
	}

	@SuppressWarnings("unchecked")
	private boolean match_function_specific(final Base_Type value, final int valueIndex, final Restricted_Length_Template template, final int templateIndex, final boolean legacy) {
		if (valueIndex >= 0) {
			return ((TitanTemplate_Array<Tvalue, Ttemplate>)template).single_value[templateIndex]
					.match(((TitanValue_Array<Tvalue>) value).array_elements[valueIndex], legacy);
		} else {
			return ((TitanTemplate_Array<Tvalue, Ttemplate>)template).single_value[templateIndex].is_any_or_omit();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean match(final Base_Type otherValue, final boolean legacy) {
		if (otherValue instanceof TitanValue_Array<?>) {
			return match((TitanValue_Array<Tvalue>) otherValue, legacy);
		}

		throw new TtcnError(MessageFormat.format("Internal Error: value {0} can not be cast to value array", otherValue));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void log_match(final Base_Type match_value, final boolean legacy) {
		if (match_value instanceof TitanValue_Array<?>) {
			log_match((TitanValue_Array<Tvalue>) match_value, legacy);
			return;
		}

		throw new TtcnError(MessageFormat.format("Internal Error: value `{0}'' can not be cast to value array", match_value));
	}

	@Override
	public void set_param(final Module_Parameter param) {
		if (param.get_id() != null) {
			if(param.get_id().getClass().equals(Module_Param_Name.class) && param.get_id().next_name()) {		
				// Haven't reached the end of the module parameter name
				// => the name refers to one of the elements, not to the whole array
				final String param_field = param.get_id().get_current_name();
				if (param_field.charAt(0) < '0' || param_field.charAt(0) > '9') {
					param.error("Unexpected record field name in module parameter, expected a valid array template index");
				}
				final int param_index = Integer.parseInt(param_field);
				if (param_index >= array_size) {
					param.error("Invalid array index: %u. The array only has %u elements.", param_index, array_size);
				}
				this.get_at(param_index).set_param(param);
				return;
			}
		}

		param.basic_check(basic_check_bits_t.BC_TEMPLATE.getValue(), "array template");

		switch (param.get_type()) {
		case MP_Omit:
			this.operator_assign(template_sel.OMIT_VALUE);
			break;
		case MP_Any:
			this.operator_assign(template_sel.ANY_VALUE);
			break;
		case MP_AnyOrNone:
			this.operator_assign(template_sel.ANY_OR_OMIT);
			break;
		case MP_List_Template:
		case MP_ComplementList_Template: {
			final TitanTemplate_Array<Tvalue, Ttemplate> temp = new TitanTemplate_Array<Tvalue, Ttemplate>(classValue, classTemplate);
			temp.set_type(param.get_type() == type_t.MP_List_Template ? template_sel.VALUE_LIST : template_sel.COMPLEMENTED_LIST, param.get_size());
			for (int i = 0; i < param.get_size(); i++) {
				temp.list_item(i).set_param(param.get_elem(i));
			}
			this.operator_assign(temp);
			break;
		}
		case MP_Value_List:
			set_size(param.get_size());
			for (int i = 0; i < param.get_size(); i++) {
				final Module_Parameter curr = param.get_elem(i);
				if (curr.get_type() != type_t.MP_NotUsed) {
					this.get_at(i + indexOffset).set_param(curr);
				}
			}
			break;
		case MP_Indexed_List:
			for (int i = 0; i < param.get_size(); i++) {
				final Module_Parameter curr = param.get_elem(i);
				this.get_at(curr.get_id().get_index()).set_param(curr);
			}
			break;
		default:
			param.type_error("array template");
			break;
		}
		is_ifPresent = param.get_ifpresent();
	}

	@Override
	/** {@inheritDoc} */
	public void encode_text(final Text_Buf text_buf) {
		encode_text_permutation(text_buf);

		switch (template_selection) {
		case OMIT_VALUE:
		case ANY_VALUE:
		case ANY_OR_OMIT:
			break;
		case SPECIFIC_VALUE:
			text_buf.push_int(singleSize);
			for (int i = 0; i < array_size; i++) {
				single_value[i].encode_text(text_buf);
			}
			break;
		case VALUE_LIST:
		case COMPLEMENTED_LIST:
			text_buf.push_int(listSize);
			for (int i = 0; i < listSize; i++) {
				value_list[i].encode_text(text_buf);
			}
			break;
		default:
			throw new TtcnError("Text encoder: Encoding an uninitialized/unsupported array template.");
		}
	}

	@Override
	/** {@inheritDoc} */
	public void decode_text(final Text_Buf text_buf) {
		clean_up();
		decode_text_permutation(text_buf);

		switch (template_selection) {
		case OMIT_VALUE:
		case ANY_VALUE:
		case ANY_OR_OMIT:
			break;
		case SPECIFIC_VALUE:
			singleSize = text_buf.pull_int().get_int();
			if (singleSize < 0) {
				throw new TtcnError("Text decoder: Negative size was received for an array template.");
			}
			single_value = new Base_Template[singleSize];
			for (int i = 0; i < array_size; i++) {
				try {
					final Ttemplate helper = classTemplate.newInstance();
					helper.decode_text(text_buf);
					single_value[i] = helper;
				} catch (InstantiationException e) {
					throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
				} catch (IllegalAccessException e) {
					throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
				}
			}
			break;
		case VALUE_LIST:
		case COMPLEMENTED_LIST:
			listSize = text_buf.pull_int().get_int();
			value_list = new TitanTemplate_Array[listSize];
			for (int i = 0; i < listSize; ++i) {
				value_list[i] = new TitanTemplate_Array<Tvalue,Ttemplate>(classValue, classTemplate);
				value_list[i].decode_text(text_buf);
			}
			break;
		default:
			throw new TtcnError("Text decoder: An unknown/unsupported selection was received for an array template.");
		}
	}

	/**
	 * Matches the provided value against this template.
	 *
	 * @param otherValue the value to be matched.
	 * */
	public boolean match(final TitanValue_Array<Tvalue> otherValue) {
		return match(otherValue,false);
	}

	/**
	 * Matches the provided value against this template. In legacy mode
	 * omitted value fields are not matched against the template field.
	 *
	 * @param otherValue
	 *                the value to be matched.
	 * @param legacy
	 *                use legacy mode.
	 * */
	public boolean match(final TitanValue_Array<Tvalue> otherValue, final boolean legacy) {
		if (!match_length(array_size)) {
			return false;
		}

		switch (template_selection) {
		case SPECIFIC_VALUE:
			final match_function_t obj = new match_function_t() {

				@Override
				public boolean match(final Base_Type value_ptr, final int value_index, final Restricted_Length_Template template_ptr,
						final int template_index, final boolean legacy) {
					return match_function_specific(value_ptr, value_index, template_ptr, template_index, legacy);
				}
			};
			return match_permutation_array(otherValue, array_size, this, singleSize, obj, legacy);
		case OMIT_VALUE:
			return false;
		case ANY_VALUE:
		case ANY_OR_OMIT:
			return true;
		case VALUE_LIST:
		case COMPLEMENTED_LIST:
			for (int i = 0; i < listSize; i++) {
				if (value_list[i].match(otherValue, legacy)) {
					return template_selection == template_sel.VALUE_LIST;
				}
			}
			return template_selection == template_sel.COMPLEMENTED_LIST;
		default:
			throw new TtcnError("Matching with an uninitialized/unsupported array template.");
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean match_omit(final boolean legacy) {
		if (is_ifPresent) {
			return true;
		}
		switch (template_selection) {
		case OMIT_VALUE:
		case ANY_OR_OMIT:
			return true;
		case VALUE_LIST:
		case COMPLEMENTED_LIST:
			if (legacy) {
				// legacy behavior: 'omit' can appear in the value/complement list
				for (int i = 0; i < listSize; i++) {
					if (value_list[i].match_omit()) {
						return template_selection == template_sel.VALUE_LIST;
					}
				}

				return template_selection == template_sel.COMPLEMENTED_LIST;
			}
			// else fall through
		default:
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public TitanTemplate_Array<Tvalue, Ttemplate> operator_assign(final Base_Type otherValue) {
		if (otherValue instanceof TitanValue_Array<?>) {
			final TitanValue_Array<Tvalue> arrayOther = (TitanValue_Array<Tvalue>)otherValue;
			return operator_assign(arrayOther);
		} else {
			try {
				final Ttemplate value = classTemplate.newInstance();
				value.operator_assign(otherValue);
				singleSize = 1;
				single_value = new Base_Template[singleSize];
				single_value[0] = value;
			} catch (InstantiationException e) {
				throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
			} catch (IllegalAccessException e) {
				throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
			}

			return this;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public TitanTemplate_Array<Tvalue, Ttemplate> operator_assign(final Base_Template otherValue) {
		if (otherValue instanceof TitanTemplate_Array<?,?>) {
			final TitanTemplate_Array<Tvalue, Ttemplate> arrayOther = (TitanTemplate_Array<Tvalue, Ttemplate>)otherValue;
			return operator_assign(arrayOther);
		} else {
			try {
				final Ttemplate value = classTemplate.newInstance();
				value.operator_assign(otherValue);
				singleSize = 1;
				single_value = new Base_Template[singleSize];
				single_value[0] = value;
			} catch (InstantiationException e) {
				throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
			} catch (IllegalAccessException e) {
				throw new TtcnError(MessageFormat.format("Internal error: class `{0}'' could not be instantiated ({1}).", classTemplate, e));
			}

			return this;
		}
	}

	private answer recursive_permutation_match(final Base_Type value_ptr,
			final int value_start_index,
			final int value_size,
			final TitanTemplate_Array<Tvalue, Ttemplate> template_ptr,
			final int template_start_index,
			final int template_size,
			final int permutation_index,
			final match_function_t match_function,
			final AtomicInteger shift_size,
			final boolean legacy) {

		final int nof_permutations = template_ptr.get_number_of_permutations();
		if (permutation_index > nof_permutations) {
			throw new TtcnError("Internal error: recursive_permutation_match: invalid argument.");
		}

		if (permutation_index < nof_permutations && template_ptr.get_permutation_end(permutation_index) > template_start_index + template_size) {
			throw new TtcnError(MessageFormat.format("Internal error: recursive_permutation_match: wrong permutation interval settings for permutation {0}.", permutation_index));
		}

		shift_size.set(0);

		//trivial cases
		if (template_size == 0) {
			//reached the end of templates
			// if we reached the end of values => good
			// else => bad
			if (value_size == 0) {
				return answer.SUCCESS;
			} else {
				return answer.FAILURE;
			}
		}

		//are we at an asterisk or at the beginning of a permutation interval
		boolean is_asterisk;
		final boolean permutation_begins = permutation_index < nof_permutations &&
				template_start_index == template_ptr.get_permutation_start(permutation_index);

		if (permutation_begins ||
				match_function.match(value_ptr, -1, template_ptr, template_start_index, legacy)) {
			int smallest_possible_size;
			int largest_possible_size;
			boolean has_asterisk;
			boolean already_superset;
			int permutation_size;

			//check how many values might be associated with this permutation
			//if we are at a permutation start
			if (permutation_begins) {
				is_asterisk = false;
				permutation_size = template_ptr.get_permutation_size(permutation_index);
				smallest_possible_size = 0;
				has_asterisk = false;

				//count how many non asterisk elements are in the permutation
				for (int i = 0; i < permutation_size; i++) {
					if (match_function.match(value_ptr, -1, template_ptr, i + template_start_index, legacy)) {
						has_asterisk = true;
					} else {
						smallest_possible_size++;
					}
				}

				//the real permutation size is bigger then the value size
				if (smallest_possible_size > value_size) {
					return answer.NO_CHANCE;
				}

				//if the permutation has an asterisk then it can grow
				if (has_asterisk) {
					largest_possible_size = value_size;

					//if there are only asterisks in the permutation
					if (smallest_possible_size == 0) {
						already_superset = true;
					} else {
						already_superset = false;
					}
				} else {
					//without asterisks its size is fixed
					largest_possible_size = smallest_possible_size;
					already_superset = false;
				}
			} else {
				//or at an asterisk
				is_asterisk = true;
				already_superset = true;
				permutation_size = 1;
				smallest_possible_size = 0;
				largest_possible_size = value_size;
				has_asterisk = true;
			}

			int temp_size = smallest_possible_size;

			{
				//this is to make match_set_of incremental,
				// we store the already found pairs in this vector
				// so we wouldn't try to find a pair for those templates again
				// and we can set the covered state of values too
				// to not waste memory it is only created if needed
				int[] pair_list = null;
				int old_temp_size = 0;

				if (!already_superset) {
					pair_list = new int[permutation_size];
					for (int i = 0; i < permutation_size; i++) {
						// in the beginning we haven't found a template to any values
						pair_list[i] = -1;
					}
				}

				while (!already_superset) {
					//must be a permutation having other values than asterisks

					final AtomicInteger x = new AtomicInteger(0);

					//our set matching is extended with 2 more parameters
					// giving back how many templates
					// (other than asterisk) couldn't be matched
					// and setting / giving back the value-template pairs

					final boolean found = RecordOf_Match.match_set_of_internal(value_ptr, value_start_index,
							temp_size, template_ptr,
							template_start_index, permutation_size,
							match_function, type_of_matching.SUPERSET, x, pair_list,old_temp_size, legacy);

					if (found) {
						already_superset = true;
					} else {
						//as we didn't found a match we have to try
						// a larger set of values
						//x is the number of templates we couldn't find
						// a matching pair for
						// the next must be at least this big to fully cover
						// on the other side if it would be bigger than it might miss
						// the smallest possible match.

						//if we can match with more values
						if (has_asterisk && temp_size + x.get() <= largest_possible_size) {
							old_temp_size = temp_size;
							temp_size += x.get();
						} else {
							return answer.FAILURE; //else we failed
						}
					}
				}
			}

			//we reach here only if we found a match

			//can only go on recursively if we haven't reached the end

			//reached the end of templates
			if (permutation_size == template_size) {
				if (has_asterisk || value_size == temp_size) {
					return answer.SUCCESS;
				} else {
					return answer.FAILURE;
				}
			}

			for (int i = temp_size; i <= largest_possible_size;) {
				answer result;

				if (is_asterisk) {
					//don't step the permutation index
					result = recursive_permutation_match(value_ptr,value_start_index + i,
							value_size - i, template_ptr,
							template_start_index + permutation_size,
							template_size - permutation_size,
							permutation_index,
							match_function, shift_size, legacy);
				} else {
					//try with the next permutation
					result = recursive_permutation_match(value_ptr,value_start_index + i,
							value_size - i, template_ptr,
							template_start_index + permutation_size,
							template_size - permutation_size,
							permutation_index + 1,
							match_function, shift_size, legacy);
				}

				if (result == answer.SUCCESS) {
					// we finished
					return answer.SUCCESS;
				} else if (result == answer.NO_CHANCE) {
					//matching is not possible
					return answer.NO_CHANCE;
				} else if (i == value_size) {
					// we failed
					// if there is no chance of matching
					return answer.NO_CHANCE;
				} else {
					i += shift_size.get() > 1 ? shift_size.get() : 1;

					if (i > largest_possible_size) {
						shift_size.set(i - largest_possible_size);
					} else {
						shift_size.set(0);
					}
				}
			}

			//this level failed;
			return answer.FAILURE;
		} else {
			//we are at the beginning of a non permutation, non asterisk interval

			//the distance to the next permutation or the end of templates
			// so the longest possible match
			int distance;

			if (permutation_index < nof_permutations) {
				distance = template_ptr.get_permutation_start(permutation_index) - template_start_index;
			} else {
				distance = template_size;
			}

			//if there are no more values, but we still have templates
			// and the template is not an asterisk or a permutation start
			if (value_size == 0) {
				return answer.FAILURE;
			}

			//we try to match as many values as possible
			//an asterisk is handled like a 0 length permutation
			boolean good;
			int i = 0;
			do {
				good = match_function.match(value_ptr, value_start_index + i,
						template_ptr, template_start_index + i, legacy);
				i++;
				//bad stop: something can't be matched
				//half bad half good stop: the end of values is reached
				//good stop: matching on the full distance or till an asterisk
			} while (good && i < value_size && i < distance
					&& !match_function.match(value_ptr, -1, template_ptr, template_start_index + i, legacy));

			//if we matched on the full distance or till an asterisk
			if (good && (i == distance ||
					match_function.match(value_ptr, -1, template_ptr, template_start_index + i, legacy))) {
				//reached the end of the templates
				if (i == template_size) {
					if (i < value_size) {
						//the next level would return FAILURE so we don't step it
						return answer.FAILURE;
					} else {
						//i == value_size, so we matched everything
						return answer.SUCCESS;
					}
				} else {
					//we reached the next asterisk or permutation,
					// so step to the next level
					return recursive_permutation_match(value_ptr,value_start_index + i,
							value_size - i,
							template_ptr,
							template_start_index + i,
							template_size - i,
							permutation_index,
							match_function, shift_size, legacy);
				}
			} else {
				//something bad happened, so we have to check how bad the situation is
				if (i == value_size) {
					//the aren't values left, meaning that the match is not possible
					return answer.NO_CHANCE;
				} else {
					//we couldn't match, but there is still a chance of matching

					//try to find a matching value for the last checked (and failed)
					// template.
					// smaller jumps would fail so we skip them
					shift_size.set(0);
					i--;
					do {
						good = match_function.match(value_ptr, value_start_index + i + shift_size.get(), template_ptr, template_start_index + i, legacy);
						shift_size.incrementAndGet();
					} while (!good && i + shift_size.get() < value_size);

					if (good) {
						shift_size.decrementAndGet();
						return answer.FAILURE;
					} else {
						// the template can not be matched later
						return answer.NO_CHANCE;
					}
				}
			}
		}
	}

	boolean match_permutation_array(final Base_Type value_ptr, final int value_size,
			final TitanTemplate_Array<Tvalue, Ttemplate> template_ptr, final int template_size,
			final match_function_t match_function, final boolean legacy) {

		if (value_ptr == null || value_size < 0 ||
				template_ptr == null || template_size < 0 ||
				template_ptr.get_selection() != template_sel.SPECIFIC_VALUE) {
			throw new TtcnError("Internal error: match_permutation_arry: invalid argument.");
		}

		final int nof_permutations = template_ptr.get_number_of_permutations();

		// use the simplified algorithm if the template does not contain permutation
		if (nof_permutations == 0) {
			return RecordOf_Match.match_array(value_ptr, value_size,
					template_ptr, template_size, match_function, legacy);
		}

		// use 'set of' matching if all template elements are grouped into one permutation
		if (nof_permutations == 1 && template_ptr.get_permutation_start(0) == 0 &&
				template_ptr.get_permutation_end(0) == (template_size - 1)) {
			return RecordOf_Match.match_set_of(value_ptr, value_size, template_ptr, template_size, match_function, legacy);
		}

		final AtomicInteger shift_size = new AtomicInteger(0);
		return recursive_permutation_match(value_ptr, 0, value_size, template_ptr,
				0, template_size, 0, match_function, shift_size, legacy) == answer.SUCCESS;
	}

	@Override
	public void log() {
		switch (template_selection) {
		case SPECIFIC_VALUE:
			if (singleSize > 0) {
				TTCN_Logger.log_event_str("{ ");
				for (int elem_count = 0; elem_count < singleSize; elem_count++) {
					if (elem_count > 0) {
						TTCN_Logger.log_event_str(", ");
					}
					if (permutation_starts_at(elem_count)) {
						TTCN_Logger.log_event_str("permutation(");
					}
					single_value[elem_count].log();
					if (permutation_ends_at(elem_count)) {
						TTCN_Logger.log_char(')');
					}
				}
				TTCN_Logger.log_event_str(" }");
			} else {
				TTCN_Logger.log_event_str("{ }");
			}
			break;
		case COMPLEMENTED_LIST:
			TTCN_Logger.log_event_str("complement");
		case VALUE_LIST:
			TTCN_Logger.log_char('(');
			for (int list_count = 0; list_count < listSize; list_count++) {
				if (list_count > 0) {
					TTCN_Logger.log_event_str(", ");
				}
				value_list[list_count].log();
			}
			TTCN_Logger.log_char(')');
			break;
		default:
			log_generic();
			break;
		}
		log_restricted();
		log_ifpresent();
	}

	/**
	 * Logs the matching of the provided value to this template, to help
	 * identify the reason for mismatch. In legacy mode omitted value fields
	 * are not matched against the template field.
	 *
	 * @param match_value
	 *                the value to be matched.
	 * @param legacy
	 *                use legacy mode.
	 * */
	public void log_match(final TitanValue_Array<Tvalue> match_value, final boolean legacy) {
		if (TTCN_Logger.matching_verbosity_t.VERBOSITY_COMPACT == TTCN_Logger.get_matching_verbosity()
				&& TTCN_Logger.get_logmatch_buffer_len() != 0) {
			TTCN_Logger.print_logmatch_buffer();
			TTCN_Logger.log_event_str(" := ");
		}
		match_value.log();
		TTCN_Logger.log_event_str(" with ");
		log();
		if (match(match_value)) {
			TTCN_Logger.log_event_str(" matched");
		} else {
			TTCN_Logger.log_event_str(" unmatched");
		}
	}

	@Override
	public void check_restriction(final template_res restriction, final String name, final boolean legacy) {
		if (template_selection == template_sel.UNINITIALIZED_TEMPLATE) {
			return;
		}

		switch ((name != null && restriction == template_res.TR_VALUE) ? template_res.TR_OMIT : restriction) {
		case TR_OMIT:
			if (template_selection == template_sel.OMIT_VALUE) {
				return;
			}
		case TR_VALUE:
			if (template_selection != template_sel.SPECIFIC_VALUE || is_ifPresent) {
				break;
			}

			for (int i = 0; i < singleSize; ++i) {
				single_value[i].check_restriction(restriction, name == null ? "array" : name, legacy);
			}
			return;
		case TR_PRESENT:
			if (!match_omit(legacy)) {
				return;
			}
			break;
		default:
			return;
		}

		throw new TtcnError(MessageFormat.format("Restriction `{0}'' on template of type {1} violated.", get_res_name(restriction), name == null ? "array" : name));
	}
}
