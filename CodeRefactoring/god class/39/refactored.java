// New class to handle form validations
class FormValidator {
    private Form<?> form;

    public FormValidator(Form<?> form) {
        this.form = form;
    }

    public void validate() {
        if (form.isEnabledInHierarchy() && form.isVisibleInHierarchy()) {
            validateComponents();
            validateFormValidators();
            form.onValidate();
            validateNestedForms();
        }
    }

    protected final void validateComponents() {
        form.visitFormComponentsPostOrder(new ValidationVisitor()
        {
            @Override
            public void validate(final FormComponent<?> formComponent)
            {
                final Form<?> form = formComponent.getForm();
                if (form == Form.this && form.isEnabledInHierarchy() && form.isVisibleInHierarchy())
                {
                    formComponent.validate();
                }
            }
        });
    }

    protected final void validateFormValidators() {
        final int count = form.formValidators_size();
        for (int i = 0; i < count; i++)
        {
            validateFormValidator(form.formValidators_get(i));
        }
    }

    protected final void validateFormValidator(final IFormValidator validator)
    {
        if (validator == null)
        {
            throw new IllegalArgumentException("Argument [[validator]] cannot be null");
        }

        final FormComponent<?>[] dependents = validator.getDependentFormComponents();

        boolean validate = true;

        if (dependents != null)
        {
            for (int j = 0; j < dependents.length; j++)
            {
                final FormComponent<?> dependent = dependents[j];
                // check if the dependent component is valid
                if (!dependent.isValid())
                {
                    validate = false;
                    break;
                }
                // check if the dependent component is visible and is attached to
                // the page
                else if (!form.isFormComponentVisibleInPage(dependent))
                {
                    if (log.isWarnEnabled())
                    {
                        log.warn("IFormValidator in form `" +
                                form.getPageRelativePath() +
                                "` depends on a component that has been removed from the page or is no longer visible. " +
                                "Offending component id `" + dependent.getId() + "`.");
                    }
                    validate = false;
                    break;
                }
            }
        }

        if (validate)
        {
            validator.validate(form);
        }
    }

    private void validateNestedForms() {
        form.visitChildren(Form.class, new IVisitor<Form<?>>()
        {
            public Object component(Form<?> form)
            {
                if (form.isEnabledInHierarchy() && form.isVisibleInHierarchy())
                {
                    form.validateComponents();
                    form.validateFormValidators();
                    form.onValidate();
                    return CONTINUE_TRAVERSAL;
                }
                return CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
            }
        });
    }
}

// New class to handle form component model updates
class FormComponentModelUpdater {
    private Form<?> form;

    public FormComponentModelUpdater(Form<?> form) {
        this.form = form;
    }

    public void updateModels() {
        form.internalUpdateFormComponentModels();
        updateNestedFormComponentModels();
    }

    private final void updateNestedFormComponentModels() {
        form.visitChildren(Form.class, new IVisitor<Form<?>>()
        {
            public Object component(Form<?> form)
            {
                if (form.isEnabledInHierarchy() && form.isVisibleInHierarchy())
                {
                    form.internalUpdateFormComponentModels();
                    return CONTINUE_TRAVERSAL;
                }
                return CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
            }
        });
    }
}

// The Form class now delegates responsibility to the new classes
public abstract class Form<T> extends WebMarkupContainer implements IFormSubmitListener, IHeaderContributor {
    // ... (Other existing code and member variables remain unchanged)

    // Use the new classes within the Form methods
    private FormValidator formValidator;
    private FormComponentModelUpdater formComponentModelUpdater;

    public Form(final String id) {
        super(id);
        setOutputMarkupId(true);
        this.formValidator = new FormValidator(this);
        this.formComponentModelUpdater = new FormComponentModelUpdater(this);
    }

    protected final void validate() {
        formValidator.validate();
    }

    protected final void updateFormComponentModels() {
        formComponentModelUpdater.updateModels();
    }

    // ... (Other methods remain unchanged, but may also delegate to the new classes)
    /**
     *
     */
    class FormDispatchRequest extends Request
    {
        private final Map<String, String[]> params = new HashMap<String, String[]>();

        private final Request realRequest;

        private final String url;

        /**
         * Construct.
         *
         * @param realRequest
         * @param url
         */
        public FormDispatchRequest(final Request realRequest, final String url)
        {
            this.realRequest = realRequest;
            this.url = realRequest.decodeURL(url);

            String queryString = this.url.substring(this.url.indexOf("?") + 1);
            RequestUtils.decodeUrlParameters(queryString, params);
        }

        /**
         * @see org.apache.wicket.Request#getLocale()
         */
        @Override
        public Locale getLocale()
        {
            return realRequest.getLocale();
        }

        /**
         * @see org.apache.wicket.Request#getParameter(java.lang.String)
         */
        @Override
        public String getParameter(String key)
        {
            String p[] = params.get(key);
            return p != null && p.length > 0 ? p[0] : null;
        }

        /**
         * @see org.apache.wicket.Request#getParameterMap()
         */
        @Override
        public Map<String, String[]> getParameterMap()
        {
            return params;
        }

        /**
         * @see org.apache.wicket.Request#getParameters(java.lang.String)
         */
        @Override
        public String[] getParameters(String key)
        {
            String[] param = params.get(key);
            if (param != null)
            {
                return param;
            }
            return new String[0];
        }

        /**
         * @see org.apache.wicket.Request#getPath()
         */
        @Override
        public String getPath()
        {
            return realRequest.getPath();
        }

        @Override
        public String getRelativePathPrefixToContextRoot()
        {
            return realRequest.getRelativePathPrefixToContextRoot();
        }

        @Override
        public String getRelativePathPrefixToWicketHandler()
        {
            return realRequest.getRelativePathPrefixToWicketHandler();
        }

        /**
         * @see org.apache.wicket.Request#getURL()
         */
        @Override
        public String getURL()
        {
            return url;
        }

        @Override
        public String getQueryString()
        {
            return realRequest.getQueryString();
        }
    }

    /**
     * Constant for specifying how a form is submitted, in this case using get.
     */
    public static final String METHOD_GET = "get";

    /**
     * Constant for specifying how a form is submitted, in this case using post.
     */
    public static final String METHOD_POST = "post";

    /** Flag that indicates this form has been submitted during this request */
    private static final short FLAG_SUBMITTED = FLAG_RESERVED1;

    /** Log. */
    private static final Logger log = LoggerFactory.getLogger(Form.class);

    private static final long serialVersionUID = 1L;

    private static final String UPLOAD_FAILED_RESOURCE_KEY = "uploadFailed";

    private static final String UPLOAD_TOO_LARGE_RESOURCE_KEY = "uploadTooLarge";

    /**
     * Any default IFormSubmittingComponent. If set, a hidden submit component will be rendered
     * right after the form tag, so that when users press enter in a textfield, this submit
     * component's action will be selected. If no default IFormSubmittingComponent is set, nothing
     * additional is rendered.
     * <p>
     * WARNING: note that this is a best effort only. Unfortunately having a 'default'
     * IFormSubmittingComponent in a form is ill defined in the standards, and of course IE has it's
     * own way of doing things.
     * </p>
     */
    private IFormSubmittingComponent defaultSubmittingComponent;

    /** multi-validators assigned to this form */
    private Object formValidators = null;

    /**
     * Maximum size of an upload in bytes. If null, the setting
     * {@link IApplicationSettings#getDefaultMaximumUploadSize()} is used.
     */
    private Bytes maxSize = null;

    /** True if the form has enctype of multipart/form-data */
    private short multiPart = 0;

    /**
     * A user has explicitly called {@link #setMultiPart(boolean)} with value {@code true}forcing it
     * to be true
     */
    private static final short MULTIPART_HARD = 0x01;

    /**
     * The form has discovered a multipart component before rendering and is marking itself as
     * multipart until next render
     */
    private static final short MULTIPART_HINT = 0x02;

    /**
     * Constructs a form with no validation.
     *
     * @param id
     *            See Component
     */
    public Form(final String id)
    {
        super(id);
        setOutputMarkupId(true);
    }

    /**
     * @param id
     *            See Component
     * @param model
     *            See Component
     * @see org.apache.wicket.Component#Component(String, IModel)
     */
    public Form(final String id, IModel<T> model)
    {
        super(id, model);
        setOutputMarkupId(true);
    }

    /**
     * Adds a form validator to the form.
     *
     * @param validator
     *            validator
     * @throws IllegalArgumentException
     *             if validator is null
     * @see IFormValidator
     * @see IValidatorAddListener
     */
    public void add(IFormValidator validator)
    {
        if (validator == null)
        {
            throw new IllegalArgumentException("Argument `validator` cannot be null");
        }

        // add the validator
        formValidators_add(validator);

        // see whether the validator listens for add events
        if (validator instanceof IValidatorAddListener)
        {
            ((IValidatorAddListener)validator).onAdded(this);
        }
    }

    /**
     * Removes a form validator from the form.
     *
     * @param validator
     *            validator
     * @throws IllegalArgumentException
     *             if validator is null
     * @see IFormValidator
     */
    public void remove(IFormValidator validator)
    {
        if (validator == null)
        {
            throw new IllegalArgumentException("Argument `validator` cannot be null");
        }

        IFormValidator removed = formValidators_remove(validator);
        if (removed == null)
        {
            throw new IllegalStateException(
                    "Tried to remove form validator that was not previously added. "
                            + "Make sure your validator's equals() implementation is sufficient");
        }
        addStateChange(new FormValidatorRemovedChange(removed));
    }

    private final int formValidators_indexOf(IFormValidator validator)
    {
        if (formValidators != null)
        {
            if (formValidators instanceof IFormValidator)
            {
                final IFormValidator v = (IFormValidator)formValidators;
                if (v == validator || v.equals(validator))
                {
                    return 0;
                }
            }
            else
            {
                final IFormValidator[] validators = (IFormValidator[])formValidators;
                for (int i = 0; i < validators.length; i++)
                {
                    final IFormValidator v = validators[i];
                    if (v == validator || v.equals(validator))
                    {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private final IFormValidator formValidators_remove(IFormValidator validator)
    {
        int index = formValidators_indexOf(validator);
        if (index != -1)
        {
            return formValidators_remove(index);
        }
        return null;
    }

    private final IFormValidator formValidators_remove(int index)
    {
        if (formValidators instanceof IFormValidator)
        {
            if (index == 0)
            {
                final IFormValidator removed = (IFormValidator)formValidators;
                formValidators = null;
                return removed;
            }
            else
            {
                throw new IndexOutOfBoundsException();
            }
        }
        else
        {
            final IFormValidator[] validators = (IFormValidator[])formValidators;
            final IFormValidator removed = validators[index];
            // check if we can collapse array of 1 element into a single object
            if (validators.length == 2)
            {
                formValidators = validators[1 - index];
            }
            else
            {
                IFormValidator[] newValidators = new IFormValidator[validators.length - 1];
                int j = 0;
                for (int i = 0; i < validators.length; i++)
                {
                    if (i != index)
                    {
                        newValidators[j++] = validators[i];
                    }
                }
                formValidators = newValidators;
            }
            return removed;
        }
    }


    /**
     * Clears the input from the form's nested children of type {@link FormComponent}. This method
     * is typically called when a form needs to be reset.
     */
    public final void clearInput()
    {
        // Visit all the (visible) form components and clear the input on each.
        visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                if (formComponent.isVisibleInHierarchy())
                {
                    // Clear input from form component
                    formComponent.clearInput();
                }
            }
        });
    }

    /**
     * Registers an error feedback message for this component
     *
     * @param error
     *            error message
     * @param args
     *            argument replacement map for ${key} variables
     */
    public final void error(String error, Map<String, Object> args)
    {
        error(new MapVariableInterpolator(error, args).toString());
    }

    /**
     * Gets the IFormSubmittingComponent which submitted this form.
     *
     * @return The component which submitted this form, or null if the processing was not triggered
     *         by a registered IFormSubmittingComponent
     */
    public final IFormSubmittingComponent findSubmittingButton()
    {
        IFormSubmittingComponent submittingComponent = (IFormSubmittingComponent)getPage().visitChildren(
                IFormSubmittingComponent.class, new IVisitor<Component>()
                {
                    public Object component(final Component component)
                    {
                        // Get submitting component
                        final IFormSubmittingComponent submittingComponent = (IFormSubmittingComponent)component;
                        final Form<?> form = submittingComponent.getForm();

                        // Check for component-name or component-name.x request string
                        if ((form != null) && (form.getRootForm() == Form.this))
                        {
                            String name = submittingComponent.getInputName();
                            if ((getRequest().getParameter(name) != null) ||
                                    (getRequest().getParameter(name + ".x") != null))
                            {
                                if (!component.isVisibleInHierarchy())
                                {
                                    throw new WicketRuntimeException("Submit Button " +
                                            submittingComponent.getInputName() + " (path=" +
                                            component.getPageRelativePath() + ") is not visible");
                                }
                                if (!component.isEnabledInHierarchy())
                                {
                                    throw new WicketRuntimeException("Submit Button " +
                                            submittingComponent.getInputName() + " (path=" +
                                            component.getPageRelativePath() + ") is not enabled");
                                }
                                return submittingComponent;
                            }
                        }
                        return CONTINUE_TRAVERSAL;
                    }
                });

        return submittingComponent;
    }

    /**
     * Gets the default IFormSubmittingComponent. If set (not null), a hidden submit component will
     * be rendered right after the form tag, so that when users press enter in a textfield, this
     * submit component's action will be selected. If no default component is set (it is null),
     * nothing additional is rendered.
     * <p>
     * WARNING: note that this is a best effort only. Unfortunately having a 'default' button in a
     * form is ill defined in the standards, and of course IE has it's own way of doing things.
     * </p>
     * There can be only one default submit component per form hierarchy. So if you want to get the
     * default component on a nested form, it will actually delegate the call to root form. </b>
     *
     * @return The submit component to set as the default IFormSubmittingComponent, or null when you
     *         want to 'unset' any previously set default IFormSubmittingComponent
     */
    public final IFormSubmittingComponent getDefaultButton()
    {
        if (isRootForm())
        {
            return defaultSubmittingComponent;
        }
        else
        {
            return getRootForm().getDefaultButton();
        }
    }

    /**
     * Gets all {@link IFormValidator}s added to this form
     *
     * @return unmodifiable collection of {@link IFormValidator}s
     */
    public final Collection<IFormValidator> getFormValidators()
    {
        final int size = formValidators_size();

        List<IFormValidator> validators = null;

        if (size == 0)
        {
            // form has no validators, use empty collection
            validators = Collections.emptyList();
        }
        else
        {
            // form has validators, copy all into collection
            validators = new ArrayList<IFormValidator>(size);
            for (int i = 0; i < size; i++)
            {
                validators.add(formValidators_get(i));
            }
        }
        return Collections.unmodifiableCollection(validators);
    }

    /**
     * This generates a piece of javascript code that sets the url in the special hidden field and
     * submits the form.
     *
     * Warning: This code should only be called in the rendering phase for form components inside
     * the form because it uses the css/javascript id of the form which can be stored in the markup.
     *
     * @param url
     *            The interface url that has to be stored in the hidden field and submitted
     * @return The javascript code that submits the form.
     */
    public final CharSequence getJsForInterfaceUrl(CharSequence url)
    {
        Form<?> root = getRootForm();
        return new AppendingStringBuffer("document.getElementById('").append(
                root.getHiddenFieldId())
                .append("').value='")
                .append(url)
                .append("';document.getElementById('")
                .append(root.getMarkupId())
                .append("').submit();");
    }

    /**
     * Gets the maximum size for uploads. If null, the setting
     * {@link IApplicationSettings#getDefaultMaximumUploadSize()} is used.
     *
     * @return the maximum size
     */
    public Bytes getMaxSize()
    {
        Bytes maxSize = this.maxSize;
        if (maxSize == null)
        {
            maxSize = (Bytes)visitChildren(Form.class, new IVisitor<Form<?>>()
            {

                public Object component(Form<?> component)
                {
                    Bytes maxSize = component.getMaxSize();
                    if (maxSize != null)
                    {
                        return maxSize;
                    }
                    return CONTINUE_TRAVERSAL;
                }

            });
        }
        if (maxSize == null)
        {
            return getApplication().getApplicationSettings().getDefaultMaximumUploadSize();
        }
        return maxSize;
    }

    /**
     * Returns the root form or this, if this is the root form.
     *
     * @return root form or this form
     */
    public Form<?> getRootForm()
    {
        Form<?> form;
        Form<?> parent = this;
        do
        {
            form = parent;
            parent = form.findParent(Form.class);
        }
        while (parent != null);

        return form;
    }

    /**
     * Returns the prefix used when building validator keys. This allows a form to use a separate
     * "set" of keys. For example if prefix "short" is returned, validator key short.Required will
     * be tried instead of Required key.
     * <p>
     * This can be useful when different designs are used for a form. In a form where error messages
     * are displayed next to their respective form components as opposed to at the top of the form,
     * the ${label} attribute is of little use and only causes redundant information to appear in
     * the message. Forms like these can return the "short" (or any other string) validator prefix
     * and declare key: short.Required=required to override the longer message which is usually
     * declared like this: Required=${label} is a required field
     * <p>
     * Returned prefix will be used for all form components. The prefix can also be overridden on
     * form component level by overriding {@link FormComponent#getValidatorKeyPrefix()}
     *
     * @return prefix prepended to validator keys
     */
    public String getValidatorKeyPrefix()
    {
        return null;
    }

    /**
     * Gets whether the current form has any error registered.
     *
     * @return True if this form has at least one error.
     */
    public final boolean hasError()
    {
        // if this form itself has an error message
        if (hasErrorMessage())
        {
            return true;
        }

        // the form doesn't have any errors, now check any nested form
        // components
        return anyFormComponentError();
    }

    /**
     * Returns whether the form is a root form, which means that there's no other form in it's
     * parent hierarchy.
     *
     * @return true if form is a root form, false otherwise
     */
    public boolean isRootForm()
    {
        return findParent(Form.class) == null;
    }

    /**
     * Checks if this form has been submitted during the current request
     *
     * @return true if the form has been submitted during this request, false otherwise
     */
    public final boolean isSubmitted()
    {
        return getFlag(FLAG_SUBMITTED);
    }

    /**
     * Method made final because we want to ensure users call setVersioned.
     *
     * @see org.apache.wicket.Component#isVersioned()
     */
    @Override
    public boolean isVersioned()
    {
        return super.isVersioned();
    }

    /**
     * THIS METHOD IS NOT PART OF THE WICKET PUBLIC API. DO NOT CALL IT.
     * <p>
     * Retrieves FormComponent values related to the page using the persister and assign the values
     * to the FormComponent. Thus initializing them.
     */
    public final void loadPersistentFormComponentValues()
    {
        visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                // Component must implement persister interface and
                // persistence for that component must be enabled.
                // Else ignore the persisted value. It'll be deleted
                // once the user submits the Form containing that FormComponent.
                // Note: if that is true, values may remain persisted longer
                // than really necessary
                if (formComponent.isPersistent() && formComponent.isVisibleInHierarchy())
                {
                    // The persister
                    final IValuePersister persister = getValuePersister();

                    // Retrieve persisted value
                    persister.load(formComponent);
                }
            }
        });
    }

    /**
     * THIS METHOD IS NOT PART OF THE WICKET API. DO NOT ATTEMPT TO OVERRIDE OR CALL IT.
     *
     * Handles form submissions.
     *
     * @see Form#validate()
     */
    public final void onFormSubmitted()
    {
        markFormsSubmitted();

        if (handleMultiPart())
        {
            // Tells FormComponents that a new user input has come
            inputChanged();

            String url = getRequest().getParameter(getHiddenFieldId());
            if (!Strings.isEmpty(url))
            {
                dispatchEvent(getPage(), url);
            }
            else
            {
                // First, see if the processing was triggered by a Wicket IFormSubmittingComponent
                final IFormSubmittingComponent submittingComponent = findSubmittingButton();

                // When processing was triggered by a Wicket IFormSubmittingComponent and that
                // component indicates it wants to be called immediately
                // (without processing), call IFormSubmittingComponent.onSubmit() right away.
                if (submittingComponent != null && !submittingComponent.getDefaultFormProcessing())
                {
                    submittingComponent.onSubmit();
                }
                else
                {
                    // this is the root form
                    Form<?> formToProcess = this;

                    // find out whether it was a nested form that was submitted
                    if (submittingComponent != null)
                    {
                        formToProcess = submittingComponent.getForm();
                    }

                    // process the form for this request
                    formToProcess.process(submittingComponent);
                }
            }
        }
        // If multi part did fail check if an error is registered and call
        // onError
        else if (hasError())
        {
            callOnError();
        }
    }

    /**
     * Process the form. Though you can override this method to provide your own algorithm, it is
     * not recommended to do so.
     *
     * <p>
     * See the class documentation for further details on the form processing
     * </p>
     *
     * @param submittingComponent
     *            component responsible for submitting the form, or <code>null</code> if none (eg
     *            the form has been submitted via the enter key or javascript calling
     *            form.onsubmit())
     *
     * @see #delegateSubmit(IFormSubmittingComponent) for an easy way to process submitting
     *      component in the default manner
     */
    public void process(IFormSubmittingComponent submittingComponent)
    {
        // save the page in case the component is removed during submit
        final Page page = getPage();
        String hiddenFieldId = getHiddenFieldId();

        // process the form for this request
        if (process())
        {
            // let clients handle further processing
            delegateSubmit(submittingComponent);
        }

        // WICKET-1912
        // If the form is stateless page parameters contain all form component
        // values. We need to remove those otherwise they get appended to action URL
        final PageParameters parameters = page.getPageParameters();
        if (parameters != null)
        {
            visitFormComponents(new FormComponent.IVisitor()
            {
                public Object formComponent(IFormVisitorParticipant formComponent)
                {
                    if (formComponent instanceof FormComponent)
                    {
                        parameters.remove(((FormComponent<?>)formComponent).getInputName());
                    }

                    return Component.IVisitor.CONTINUE_TRAVERSAL;
                }
            });
            parameters.remove(hiddenFieldId);
            if (submittingComponent instanceof AbstractSubmitLink)
            {
                AbstractSubmitLink submitLink = (AbstractSubmitLink)submittingComponent;
                parameters.remove(submitLink.getInputName());
            }
        }
    }

    /**
     * Called after form components have updated their models. This is a late-stage validation that
     * allows outside frameworks to validate any beans that the form is updating.
     *
     * This validation method is not preferred because at this point any errors will not unroll any
     * changes to the model object, so the model object is in a modified state potentially
     * containing illegal values. However, with external frameworks there may not be an alternate
     * way to validate the model object. A good example of this is a JSR303 Bean Validator
     * validating the model object to check any class-level constraints, in order to check such
     * constaints the model object must contain the values set by the user.
     */
    protected void onValidateModelObjects()
    {

    }

    /**
     * Process the form. Though you can override this method to provide your whole own algorithm, it
     * is not recommended to do so.
     * <p>
     * See the class documentation for further details on the form processing
     * </p>
     *
     * @deprecated use {@link #process(IFormSubmittingComponent)}
     *
     * @return False if the form had an error
     */
    @Deprecated
    public boolean process()
    {
        if (!isEnabledInHierarchy() || !isVisibleInHierarchy())
        {
            // since process() can be called outside of the default form workflow, an additional
            // check is needed
            return false;
        }

        // run validation
        validate();

        // If a validation error occurred
        if (hasError())
        {
            // mark all children as invalid
            markFormComponentsInvalid();

            // let subclass handle error
            callOnError();

            // Form has an error
            return false;
        }
        else
        {
            // mark all children as valid
            markFormComponentsValid();

            // before updating, call the interception method for clients
            beforeUpdateFormComponentModels();

            // Update model using form data
            updateFormComponentModels();

            onValidateModelObjects();
            if (hasError())
            {
                callOnError();
                return false;
            }

            // Persist FormComponents if requested
            persistFormComponentData();

            // Form has no error
            return true;
        }
    }

    /**
     * Calls onError on this {@link Form} and any enabled and visible nested form, if the respective
     * {@link Form} actually has errors.
     */
    protected void callOnError()
    {
        onError();
        // call onError on nested forms
        visitChildren(Form.class, new IVisitor<Component>()
        {
            public Object component(Component component)
            {
                final Form<?> form = (Form<?>)component;
                if (!form.isEnabledInHierarchy() || !form.isVisibleInHierarchy())
                {
                    return IVisitor.CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
                }
                if (form.hasError())
                {
                    form.onError();
                }
                return IVisitor.CONTINUE_TRAVERSAL;
            }
        });
    }


    /**
     * Sets FLAG_SUBMITTED to true on this form and every enabled nested form.
     */
    private void markFormsSubmitted()
    {
        setFlag(FLAG_SUBMITTED, true);

        visitChildren(Form.class, new IVisitor<Component>()
        {
            public Object component(Component component)
            {
                Form<?> form = (Form<?>)component;
                if (form.isEnabledInHierarchy() && isVisibleInHierarchy())
                {
                    form.setFlag(FLAG_SUBMITTED, true);
                    return IVisitor.CONTINUE_TRAVERSAL;
                }
                return IVisitor.CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
            }
        });
    }

    /**
     * Removes already persisted data for all FormComponent children and disable persistence for the
     * same components.
     *
     * @see Page#removePersistedFormData(Class, boolean)
     *
     * @param disablePersistence
     *            if true, disable persistence for all FormComponents on that page. If false, it
     *            will remain unchanged.
     */
    public void removePersistentFormComponentValues(final boolean disablePersistence)
    {
        // The persistence manager responsible to persist and retrieve
        // FormComponent data
        final IValuePersister persister = getValuePersister();

        // Search for FormComponents like TextField etc.
        visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                if (formComponent.isVisibleInHierarchy())
                {
                    // remove the FormComponent's persisted data
                    persister.clear(formComponent);

                    // Disable persistence if requested. Leave unchanged
                    // otherwise.
                    if (formComponent.isPersistent() && disablePersistence)
                    {
                        formComponent.setPersistent(false);
                    }
                }
            }
        });
    }

    /**
     * Sets the default IFormSubmittingComponent. If set (not null), a hidden submit component will
     * be rendered right after the form tag, so that when users press enter in a textfield, this
     * submit component's action will be selected. If no default component is set (so unset by
     * calling this method with null), nothing additional is rendered.
     * <p>
     * WARNING: note that this is a best effort only. Unfortunately having a 'default' button in a
     * form is ill defined in the standards, and of course IE has it's own way of doing things.
     * </p>
     * There can be only one default button per form hierarchy. So if you set default button on a
     * nested form, it will actually delegate the call to root form. </b>
     *
     * @param submittingComponent
     *            The component to set as the default submitting component, or null when you want to
     *            'unset' any previously set default component
     */
    public final void setDefaultButton(IFormSubmittingComponent submittingComponent)
    {
        if (isRootForm())
        {
            defaultSubmittingComponent = submittingComponent;
        }
        else
        {
            getRootForm().setDefaultButton(submittingComponent);
        }
    }

    /**
     * Sets the maximum size for uploads. If null, the setting
     * {@link IApplicationSettings#getDefaultMaximumUploadSize()} is used.
     *
     * @param maxSize
     *            The maximum size
     */
    public void setMaxSize(final Bytes maxSize)
    {
        this.maxSize = maxSize;
    }

    /**
     * Set to true to use enctype='multipart/form-data', and to process file uploads by default
     * multiPart = false
     *
     * @param multiPart
     *            whether this form should behave as a multipart form
     */
    public void setMultiPart(boolean multiPart)
    {
        if (multiPart)
        {
            this.multiPart |= MULTIPART_HARD;
        }
        else
        {
            this.multiPart &= ~MULTIPART_HARD;
        }
    }

    /**
     * @see org.apache.wicket.Component#setVersioned(boolean)
     */
    @Override
    public final Component setVersioned(final boolean isVersioned)
    {
        super.setVersioned(isVersioned);

        // Search for FormComponents like TextField etc.
        visitFormComponents(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                formComponent.setVersioned(isVersioned);
            }
        });
        return this;
    }

    /**
     * Convenient and typesafe way to visit all the form components on a form.
     *
     * @param visitor
     *            The visitor interface to call
     */
    public final void visitFormComponents(final FormComponent.IVisitor visitor)
    {
        visitChildren(FormComponent.class, new IVisitor<Component>()
        {
            public Object component(final Component component)
            {
                visitor.formComponent((FormComponent<?>)component);
                return CONTINUE_TRAVERSAL;
            }
        });

        visitChildrenInContainingBorder(visitor);
    }

    /**
     * Convenient and typesafe way to visit all the form components on a form postorder (deepest
     * first)
     *
     * @param visitor
     *            The visitor interface to call
     */
    public final void visitFormComponentsPostOrder(final FormComponent.IVisitor visitor)
    {
        FormComponent.visitFormComponentsPostOrder(this, visitor);

        if (getParent() instanceof Border)
        {
            FormComponent.visitFormComponentsPostOrder(getParent(), visitor);
        }
    }

    /**
     * TODO Post 1.2 General: Maybe we should re-think how Borders are implemented, because there
     * are just too many exceptions in the code base because of borders. This time it is to solve
     * the problem tested in BoxBorderTestPage_3 where the Form is defined in the box border and the
     * FormComponents are in the "body". Thus, the formComponents are not children of the form. They
     * are rather children of the border, as the Form itself.
     *
     * @param visitor
     *            The {@link Component}.{@link IVisitor} used to visit the children.
     */
    private void visitChildrenInContainingBorder(final FormComponent.IVisitor visitor)
    {
        if (getParent() instanceof Border)
        {
            MarkupContainer border = getParent();
            Iterator<? extends Component> iter = border.iterator();
            while (iter.hasNext())
            {
                Component child = iter.next();
                if (child instanceof FormComponent)
                {
                    visitor.formComponent((FormComponent<?>)child);
                }
            }
        }
    }

    /**
     * Find out whether there is any registered error for a form component.
     *
     * @return whether there is any registered error for a form component
     */
    private boolean anyFormComponentError()
    {
        final boolean[] error = new boolean[] { false };

        // TODO not sure why we need that class. We only use the callback method.
        final IVisitor<Component> visitor = new IVisitor<Component>()
        {
            public Object component(final Component component)
            {
                if (component.hasErrorMessage())
                {
                    error[0] = true;
                    return Component.IVisitor.STOP_TRAVERSAL;
                }

                // Traverse all children
                return Component.IVisitor.CONTINUE_TRAVERSAL;
            }
        };

        // Iterator over all children and grand children. Any component may have registered an error
        // message. Do NOT restrict to Form and FormComponents.
        visitChildren(Component.class, new IVisitor<Component>()
        {
            public Object component(final Component component)
            {
                return visitor.component(component);
            }
        });

        // Borders need special treatment
        if (!error[0] && (getParent() instanceof Border))
        {
            MarkupContainer border = getParent();
            border.visitChildren(Component.class, new IVisitor<Component>()
            {
                public Object component(final Component component)
                {
                    if ((component == Form.this) || !(component instanceof FormComponent))
                    {
                        return Component.IVisitor.CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
                    }

                    return visitor.component(component);
                }
            });
        }

        return error[0];
    }

    /**
     * Method for dispatching/calling a interface on a page from the given url. Used by
     * {@link org.apache.wicket.markup.html.form.Form#onFormSubmitted()} for dispatching events
     *
     * @param page
     *            The page where the event should be called on.
     * @param url
     *            The url which describes the component path and the interface to be called.
     */
    private void dispatchEvent(final Page page, final String url)
    {
        RequestCycle rc = RequestCycle.get();
        IRequestCycleProcessor processor = rc.getProcessor();
        final RequestParameters requestParameters = processor.getRequestCodingStrategy().decode(
                new FormDispatchRequest(rc.getRequest(), url));
        IRequestTarget rt = processor.resolve(rc, requestParameters);
        if (rt instanceof IListenerInterfaceRequestTarget)
        {
            IListenerInterfaceRequestTarget interfaceTarget = ((IListenerInterfaceRequestTarget)rt);
            interfaceTarget.getRequestListenerInterface().invoke(page, interfaceTarget.getTarget());
        }
        else
        {
            throw new WicketRuntimeException(
                    "Attempt to access unknown request listener interface " +
                            requestParameters.getInterfaceName());
        }
    }

    /**
     * @param validator
     *            The form validator to add to the formValidators Object (which may be an array of
     *            IFormValidators or a single instance, for efficiency)
     */
    private void formValidators_add(final IFormValidator validator)
    {
        if (formValidators == null)
        {
            formValidators = validator;
        }
        else
        {
            // Get current list size
            final int size = formValidators_size();

            // Create array that holds size + 1 elements
            final IFormValidator[] validators = new IFormValidator[size + 1];

            // Loop through existing validators copying them
            for (int i = 0; i < size; i++)
            {
                validators[i] = formValidators_get(i);
            }

            // Add new validator to the end
            validators[size] = validator;

            // Save new validator list
            formValidators = validators;
        }
    }

    /**
     * Gets form validator from formValidators Object (which may be an array of IFormValidators or a
     * single instance, for efficiency) at the given index
     *
     * @param index
     *            The index of the validator to get
     * @return The form validator
     */
    private IFormValidator formValidators_get(int index)
    {
        if (formValidators == null)
        {
            throw new IndexOutOfBoundsException();
        }
        if (formValidators instanceof IFormValidator[])
        {
            return ((IFormValidator[])formValidators)[index];
        }
        return (IFormValidator)formValidators;
    }

    /**
     * @return The number of form validators in the formValidators Object (which may be an array of
     *         IFormValidators or a single instance, for efficiency)
     */
    private int formValidators_size()
    {
        if (formValidators == null)
        {
            return 0;
        }
        if (formValidators instanceof IFormValidator[])
        {
            return ((IFormValidator[])formValidators).length;
        }
        return 1;
    }

    /**
     * Visits the form's children FormComponents and inform them that a new user input is available
     * in the Request
     */
    private void inputChanged()
    {
        visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                if (formComponent.isVisibleInHierarchy())
                {
                    formComponent.inputChanged();
                }
            }
        });
    }

    /**
     * Persist (e.g. Cookie) FormComponent data to be reloaded and re-assigned to the FormComponent
     * automatically when the page is visited by the user next time.
     *
     * @see org.apache.wicket.markup.html.form.FormComponent#updateModel()
     */
    private void persistFormComponentData()
    {
        // Cannot add cookies to request cycle unless it accepts them
        // We could conceivably be HTML over some other protocol!
        if (getRequestCycle() instanceof WebRequestCycle)
        {
            // The persistence manager responsible to persist and retrieve
            // FormComponent data
            final IValuePersister persister = getValuePersister();

            // Search for FormComponent children. Ignore all other
            visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
            {
                @Override
                public void onFormComponent(final FormComponent<?> formComponent)
                {
                    if (formComponent.isVisibleInHierarchy())
                    {
                        // If persistence is switched on for that FormComponent
                        // ...
                        if (formComponent.isPersistent())
                        {
                            // Save component's data (e.g. in a cookie)
                            persister.save(formComponent);
                        }
                        else
                        {
                            // Remove component's data (e.g. cookie)
                            persister.clear(formComponent);
                        }
                    }
                }
            });
        }
    }

    /**
     * If a default IFormSubmittingComponent was set on this form, this method will be called to
     * render an extra field with an invisible style so that pressing enter in one of the textfields
     * will do a form submit using this component. This method is overridable as what we do is best
     * effort only, and may not what you want in specific situations. So if you have specific
     * usability concerns, or want to follow another strategy, you may override this method.
     *
     * @param markupStream
     *            The markup stream
     * @param openTag
     *            The open tag for the body
     */
    protected void appendDefaultButtonField(final MarkupStream markupStream,
                                            final ComponentTag openTag)
    {

        AppendingStringBuffer buffer = new AppendingStringBuffer();

        // div that is not visible (but not display:none either)
        buffer.append(HIDDEN_DIV_START);

        // add an empty textfield (otherwise IE doesn't work)
        buffer.append("<input type=\"text\" autocomplete=\"false\"/>");

        // add the submitting component
        final Component submittingComponent = (Component)defaultSubmittingComponent;
        buffer.append("<input type=\"submit\" name=\"");
        buffer.append(defaultSubmittingComponent.getInputName());
        buffer.append("\" onclick=\" var b=document.getElementById('");
        buffer.append(submittingComponent.getMarkupId());
        buffer.append("'); if (b!=null&amp;&amp;b.onclick!=null&amp;&amp;typeof(b.onclick) != 'undefined') {  var r = b.onclick.bind(b)(); if (r != false) b.click(); } else { b.click(); };  return false;\" ");
        buffer.append(" />");

        // close div
        buffer.append("</div>");

        getResponse().write(buffer);
    }

    /**
     * Template method to allow clients to do any processing (like recording the current model so
     * that, in case onSubmit does further validation, the model can be rolled back) before the
     * actual updating of form component models is done.
     */
    protected void beforeUpdateFormComponentModels()
    {
    }

    /**
     * Called (by the default implementation of 'process') when all fields validated, the form was
     * updated and it's data was allowed to be persisted. It is meant for delegating further
     * processing to clients.
     * <p>
     * This implementation first finds out whether the form processing was triggered by a nested
     * IFormSubmittingComponent of this form. If that is the case, that component's onSubmit is
     * called first.
     * </p>
     * <p>
     * Regardless of whether a submitting component was found, the form's onSubmit method is called
     * next.
     * </p>
     *
     * @param submittingComponent
     *            the component that triggered this form processing, or null if the processing was
     *            triggered by something else (like a non-Wicket submit button or a javascript
     *            execution)
     */
    protected void delegateSubmit(IFormSubmittingComponent submittingComponent)
    {
        // when the given submitting component is not null, it means that it was the
        // submitting component
        Form<?> formToProcess = this;
        if (submittingComponent != null)
        {
            // use the form which the submittingComponent has submitted for further processing
            formToProcess = submittingComponent.getForm();
            submittingComponent.onSubmit();
        }

        // Model was successfully updated with valid data
        formToProcess.onSubmit();

        // call onSubmit on nested forms
        formToProcess.visitChildren(Form.class, new IVisitor<Form<?>>()
        {
            public Object component(Form<?> component)
            {
                Form<?> form = component;
                if (form.isEnabledInHierarchy() && form.isVisibleInHierarchy())
                {
                    form.onSubmit();
                    return IVisitor.CONTINUE_TRAVERSAL;
                }
                return IVisitor.CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
            }
        });
    }

    /**
     * Returns the HiddenFieldId which will be used as the name and id property of the hiddenfield
     * that is generated for event dispatches.
     *
     * @return The name and id of the hidden field.
     */
    public final String getHiddenFieldId()
    {
        String formId;
        if (!getPage().isPageStateless())
        {
            // only assigned inside statefull pages WICKET-3438
            formId = getMarkupId();
        }
        else
        {
            formId = Form.getRootFormRelativeId(this).replace(":", "_");
        }
        return getInputNamePrefix() + formId + "_hf_0";
    }

    /**
     * Returns the javascript/css id of this form that will be used to generated the id="xxx"
     * attribute.
     *
     * @return The javascript/css id of this form.
     * @deprecated use {@link #getMarkupId()}
     */
    @Deprecated
    protected final String getJavascriptId()
    {
        return getMarkupId();
    }

    /**
     * Gets the HTTP submit method that will appear in form markup. If no method is specified in the
     * template, "post" is the default. Note that the markup-declared HTTP method may not correspond
     * to the one actually used to submit the form; in an Ajax submit, for example, JavaScript event
     * handlers may submit the form with a "get" even when the form method is declared as "post."
     * Therefore this method should not be considered a guarantee of the HTTP method used, but a
     * value for the markup only. Override if you have a requirement to alter this behavior.
     *
     * @return the submit method specified in markup.
     */
    protected String getMethod()
    {
        String method = getMarkupAttributes().getString("method");
        return (method != null) ? method : METHOD_POST;
    }

    /**
     *
     * @see org.apache.wicket.Component#getStatelessHint()
     */
    @Override
    protected boolean getStatelessHint()
    {
        return false;
    }

    /**
     * Gets the form component persistence manager; it is lazy loaded.
     *
     * @return The form component value persister
     */
    protected IValuePersister getValuePersister()
    {
        return new CookieValuePersister();
    }

    private boolean isMultiPart()
    {
        if (multiPart != 0)
        {
            return true;
        }

        final boolean[] anyEmbeddedMultipart = new boolean[] { false };
        visitChildren(Component.class, new IVisitor<Component>()
        {
            public Object component(Component component)
            {
                boolean isMultiPart = false;
                if (component instanceof Form)
                {
                    Form<?> form = (Form<?>)component;

                    if (form.isVisibleInHierarchy() && form.isEnabledInHierarchy())
                    {
                        isMultiPart = (form.multiPart != 0);
                    }
                }
                else if (component instanceof FormComponent)
                {
                    FormComponent<?> fc = (FormComponent<?>)component;
                    if (fc.isVisibleInHierarchy() && fc.isEnabledInHierarchy())
                    {
                        isMultiPart = fc.isMultiPart();
                    }
                }

                if (isMultiPart == true)
                {
                    anyEmbeddedMultipart[0] = true;
                    return STOP_TRAVERSAL;
                }
                return CONTINUE_TRAVERSAL;
            }

        });

        if (anyEmbeddedMultipart[0])
        {
            multiPart |= MULTIPART_HINT;
        }
        return anyEmbeddedMultipart[0];
    }

    /**
     * Handles multi-part processing of the submitted data.
     *
     * WARNING
     *
     * If this method is overridden it can break {@link FileUploadField}s on this form
     *
     * @return false if form is multipart and upload failed
     */
    protected boolean handleMultiPart()
    {
        if (isMultiPart())
        {
            // Change the request to a multipart web request so parameters are
            // parsed out correctly
            try
            {
                final WebRequest multipartWebRequest = ((WebRequest)getRequest()).newMultipartWebRequest(getMaxSize());
                getRequestCycle().setRequest(multipartWebRequest);
            }
            catch (WicketRuntimeException wre)
            {
                if (wre.getCause() == null || !(wre.getCause() instanceof FileUploadException))
                {
                    throw wre;
                }

                FileUploadException e = (FileUploadException)wre.getCause();

                // Create model with exception and maximum size values
                final Map<String, Object> model = new HashMap<String, Object>();
                model.put("exception", e);
                model.put("maxSize", getMaxSize());

                onFileUploadException((FileUploadException)wre.getCause(), model);

                // don't process the form if there is a FileUploadException
                return false;
            }
        }
        return true;
    }

    /**
     * The default message may look like ".. may not exceed 10240 Bytes..". Which is ok, but
     * sometimes you may want something like "10KB". By subclassing this method you may replace
     * maxSize in the model or add you own property and use that in your error message.
     * <p>
     * Don't forget to call super.onFileUploadException(e, model) at the end of your method.
     *
     * @param e
     * @param model
     */
    protected void onFileUploadException(final FileUploadException e,
                                         final Map<String, Object> model)
    {
        if (e instanceof SizeLimitExceededException)
        {
            // Resource key should be <form-id>.uploadTooLarge to
            // override default message
            final String defaultValue = "Upload must be less than " + getMaxSize();
            String msg = getString(getId() + "." + UPLOAD_TOO_LARGE_RESOURCE_KEY,
                    Model.ofMap(model), defaultValue);
            error(msg);
        }
        else
        {
            // Resource key should be <form-id>.uploadFailed to override
            // default message
            final String defaultValue = "Upload failed: " + e.getLocalizedMessage();
            String msg = getString(getId() + "." + UPLOAD_FAILED_RESOURCE_KEY, Model.ofMap(model),
                    defaultValue);
            error(msg);

            log.warn(msg, e);
        }
    }

    /**
     * @see org.apache.wicket.Component#internalOnModelChanged()
     */
    @Override
    protected void internalOnModelChanged()
    {
        // Visit all the form components and validate each
        visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                // If form component is using form model
                if (formComponent.sameInnermostModel(Form.this))
                {
                    formComponent.modelChanged();
                }
            }
        });
    }

    /**
     * Mark each form component on this form invalid.
     */
    protected final void markFormComponentsInvalid()
    {
        // call invalidate methods of all nested form components
        visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                if (formComponent.isVisibleInHierarchy())
                {
                    formComponent.invalid();
                }
            }
        });
    }

    /**
     * Mark each form component on this form and on nested forms valid.
     */
    protected final void markFormComponentsValid()
    {
        internalMarkFormComponentsValid();
        markNestedFormComponentsValid();
    }

    /**
     * Mark each form component on nested form valid.
     */
    private void markNestedFormComponentsValid()
    {
        visitChildren(Form.class, new IVisitor<Form<?>>()
        {
            public Object component(Form<?> component)
            {
                Form<?> form = component;
                if (form.isEnabledInHierarchy() && form.isVisibleInHierarchy())
                {
                    form.internalMarkFormComponentsValid();
                    return CONTINUE_TRAVERSAL;
                }
                return CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
            }
        });
    }

    /**
     * Mark each form component on this form valid.
     */
    private void internalMarkFormComponentsValid()
    {
        // call valid methods of all nested form components
        visitFormComponentsPostOrder(new FormComponent.AbstractVisitor()
        {
            @Override
            public void onFormComponent(final FormComponent<?> formComponent)
            {
                if (formComponent.getForm() == Form.this && formComponent.isVisibleInHierarchy())
                {
                    formComponent.valid();
                }
            }
        });
    }

    /**
     * @see org.apache.wicket.Component#onComponentTag(ComponentTag)
     */
    @Override
    protected void onComponentTag(final ComponentTag tag)
    {
        super.onComponentTag(tag);

        checkComponentTag(tag, "form");

        if (isRootForm())
        {
            String method = getMethod().toLowerCase();
            tag.put("method", method);
            String url = urlFor(IFormSubmitListener.INTERFACE).toString();
            if (encodeUrlInHiddenFields())
            {
                int i = url.indexOf('?');
                String action = (i > -1) ? url.substring(0, i) : "";
                tag.put("action", action);
                // alternatively, we could just put an empty string here, so
                // that mounted paths stay in good order. I decided against this
                // as I'm not sure whether that could have side effects with
                // other encoders
            }
            else
            {
                tag.put("action", Strings.escapeMarkup(url));
            }

            if (isMultiPart())
            {
                tag.put("enctype", "multipart/form-data");
            }
            else
            {
                // sanity check
                String enctype = (String)tag.getAttributes().get("enctype");
                if ("multipart/form-data".equalsIgnoreCase(enctype))
                {
                    // though not set explicitly in Java, this is a multipart
                    // form
                    setMultiPart(true);
                }
            }
        }
        else
        {
            tag.setName("div");
            tag.remove("method");
            tag.remove("action");
            tag.remove("enctype");
            // see renderhead for some non-root javascript markers
        }
    }


    @Override
    protected void renderPlaceholderTag(ComponentTag tag, Response response)
    {
        if (isRootForm())
        {
            super.renderPlaceholderTag(tag, response);
        }
        else
        {
            // rewrite inner form tag as div
            response.write("<div style=\"display:none\"");
            if (getOutputMarkupId())
            {
                response.write(" id=\"");
                response.write(getMarkupId());
                response.write("\"");
            }
            response.write("></div>");
        }
    }

    /**
     *
     * @return true if form's method is 'get'
     */
    protected boolean encodeUrlInHiddenFields()
    {
        String method = getMethod().toLowerCase();
        return method.equals("get");
    }

    /**
     * Append an additional hidden input tag to support anchor tags that can submit a form.
     *
     * @param markupStream
     *            The markup stream
     * @param openTag
     *            The open tag for the body
     */
    @Override
    protected void onComponentTagBody(final MarkupStream markupStream, final ComponentTag openTag)
    {
        if (isRootForm())
        {
            // get the hidden field id
            String nameAndId = getHiddenFieldId();

            // render the hidden field
            AppendingStringBuffer buffer = new AppendingStringBuffer(HIDDEN_DIV_START).append(
                    "<input type=\"hidden\" name=\"")
                    .append(nameAndId)
                    .append("\" id=\"")
                    .append(nameAndId)
                    .append("\" />");

            // if it's a get, did put the parameters in the action attribute,
            // and have to write the url parameters as hidden fields
            if (encodeUrlInHiddenFields())
            {
                String url = urlFor(IFormSubmitListener.INTERFACE).toString();
                int i = url.indexOf('?');
                String[] params = ((i > -1) ? url.substring(i + 1) : url).split("&");

                writeParamsAsHiddenFields(params, buffer);
            }
            buffer.append("</div>");
            getResponse().write(buffer);

            // if a default submitting component was set, handle the rendering of that
            if (defaultSubmittingComponent instanceof Component)
            {
                final Component submittingComponent = (Component)defaultSubmittingComponent;
                if (submittingComponent.isVisibleInHierarchy() &&
                        submittingComponent.isEnabledInHierarchy())
                {
                    appendDefaultButtonField(markupStream, openTag);
                }
            }
        }

        // do the rest of the processing
        super.onComponentTagBody(markupStream, openTag);
    }

    /**
     *
     * @param params
     * @param buffer
     */
    protected void writeParamsAsHiddenFields(String[] params, AppendingStringBuffer buffer)
    {
        for (int j = 0; j < params.length; j++)
        {
            String[] pair = params[j].split("=");

            buffer.append("<input type=\"hidden\" name=\"")
                    .append(recode(pair[0]))
                    .append("\" value=\"")
                    .append(pair.length > 1 ? recode(pair[1]) : "")
                    .append("\" />");
        }
    }

    /**
     * Take URL-encoded query string value, unencode it and return HTML-escaped version
     *
     * @param s
     *            value to reencode
     * @return reencoded value
     */
    private String recode(String s)
    {
        String un = WicketURLDecoder.QUERY_INSTANCE.decode(s);
        return Strings.escapeMarkup(un).toString();
    }

    /**
     * @see org.apache.wicket.Component#onDetach()
     */
    @Override
    protected void onDetach()
    {
        super.internalOnDetach();
        setFlag(FLAG_SUBMITTED, false);

        for (IFormValidator validator : getFormValidators())
        {
            if (validator != null && (validator instanceof IDetachable))
            {
                ((IDetachable)validator).detach();
            }
        }

        super.onDetach();
    }

    /**
     * Method to override if you want to do something special when an error occurs (other than
     * simply displaying validation errors).
     */
    protected void onError()
    {
    }

    @Override
    protected void onBeforeRender()
    {
        // clear multipart hint, it will be set if necessary by the visitor
        this.multiPart &= ~MULTIPART_HINT;

        super.onBeforeRender();
    }

    /**
     * Implemented by subclasses to deal with form submits.
     */
    protected void onSubmit()
    {
    }

    /**
     * Update the model of all components on this form and nested forms using the fields that were
     * sent with the current request. This method only updates models when the Form.validate() is
     * called first that takes care of the conversion for the FormComponents.
     *
     * Normally this method will not be called when a validation error occurs in one of the form
     * components.
     *
     * @see org.apache.wicket.markup.html.form.FormComponent#updateModel()
     */
    protected final void updateFormComponentModels()
    {
        internalUpdateFormComponentModels();
        updateNestedFormComponentModels();
    }

    /**
     * Update the model of all components on nested forms.
     *
     * @see #updateFormComponentModels()
     */
    private final void updateNestedFormComponentModels()
    {
        visitChildren(Form.class, new IVisitor<Form<?>>()
        {
            public Object component(Form<?> form)
            {
                if (form.isEnabledInHierarchy() && form.isVisibleInHierarchy())
                {
                    form.internalUpdateFormComponentModels();
                    return CONTINUE_TRAVERSAL;
                }
                return CONTINUE_TRAVERSAL_BUT_DONT_GO_DEEPER;
            }
        });
    }

    /**
     * Update the model of all components on this form.
     *
     * @see #updateFormComponentModels()
     */
    private void internalUpdateFormComponentModels()
    {
        FormComponent.visitComponentsPostOrder(this, new FormModelUpdateVisitor(this));

        MarkupContainer border = findParent(Border.class);
        if (border != null)
        {
            FormComponent.visitComponentsPostOrder(border, new FormModelUpdateVisitor(this));
        }
    }
}