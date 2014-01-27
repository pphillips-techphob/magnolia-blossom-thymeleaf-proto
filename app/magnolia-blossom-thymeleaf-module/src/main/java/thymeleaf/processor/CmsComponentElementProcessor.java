package thymeleaf.processor;

import info.magnolia.context.MgnlContext;
import info.magnolia.jcr.util.ContentMap;
import info.magnolia.module.blossom.support.IncludeRequestWrapper;
import info.magnolia.module.blossom.template.BlossomTemplateDefinition;
import info.magnolia.module.blossom.template.HandlerMetaData;
import info.magnolia.objectfactory.Components;
import info.magnolia.rendering.context.RenderingContext;
import info.magnolia.rendering.engine.RenderingEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.Arguments;
import org.thymeleaf.dom.Comment;
import org.thymeleaf.dom.Element;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.processor.ProcessorResult;
import org.thymeleaf.standard.expression.StandardExpressionProcessor;
import org.thymeleaf.standard.processor.attr.StandardFragmentAttrProcessor;

import thymeleaf.blossom.ThymeleafTemplateExporter;
import thymeleaf.magnolia.ThymeleafComponentElement;

/**
 * Created with IntelliJ IDEA. User: tkratz Date: 11.11.12 Time: 09:39 To change this template use File | Settings |
 * File Templates.
 */
public class CmsComponentElementProcessor extends AbstractRecursiveInclusionProcessor {

    private static final String FRAGMENT_ATTR_NAME = StandardFragmentAttrProcessor.ATTR_NAME;
    public static final String ATTR_NAME = "component";

    private ThymeleafTemplateExporter templateExporter;
    private List<HandlerMapping> handlerMappings;
    private List<HandlerAdapter> handlerAdapters;

    public CmsComponentElementProcessor(ApplicationContext ctx, ServletContext sctx) {

        super(ctx, sctx, ATTR_NAME);
        this.templateExporter = ctx.getBean(ThymeleafTemplateExporter.class);

        initGHandlerAdapters();
        initHandlerMappings();
    }

    private void initHandlerMappings() {
        Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
                HandlerMapping.class, true, false);
        if (!matchingBeans.isEmpty()) {
            this.handlerMappings = new ArrayList<>(matchingBeans.values());
            // We keep HandlerMappings in sorted order.
            OrderComparator.sort(this.handlerMappings);
        }
    }

    private void initGHandlerAdapters() {
        Map<String, HandlerAdapter> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
                HandlerAdapter.class, true, false);
        if (!matchingBeans.isEmpty()) {
            this.handlerAdapters = new ArrayList<>(matchingBeans.values());
            // We keep HandlerAdapters in sorted order.
            OrderComparator.sort(this.handlerAdapters);
        }
    }

    private ThymeleafComponentElement createComponentElement(RenderingContext renderingContext) {

        return Components.getComponentProvider().newInstance(ThymeleafComponentElement.class, renderingContext);
    }

    @Override
    public int getPrecedence() {
        return 1000;
    }

    @Override
    protected String getTargetAttributeName(final Arguments arguments, final Element element,
            final String attributeName, final String attributeValue) {

        if (attributeName != null) {
            final String prefix = "th";
            if (prefix != null) {
                return prefix + ":" + FRAGMENT_ATTR_NAME;
            }
        }
        return FRAGMENT_ATTR_NAME;

    }

    protected boolean getSubstituteInclusionNode(final Arguments arguments, final Element element,
            final String attributeName, final String attributeValue) {

        return true;
    }

    @Override
    public final ProcessorResult processAttribute(final Arguments arguments, final Element element,
            final String attributeName) {

        HttpServletRequest request = MgnlContext.getWebContext().getRequest();
        final HttpServletResponse response = MgnlContext.getWebContext().getResponse();

        javax.jcr.Node content = null;
        Object contentObject = StandardExpressionProcessor.processExpression(arguments,
                element.getAttributeValue(attributeName));
        if (contentObject instanceof ContentMap) {
            content = ((ContentMap) contentObject).getJCRNode();
        } else if (contentObject instanceof javax.jcr.Node) {
            content = (javax.jcr.Node) contentObject;
        } else {
            throw new TemplateProcessingException("Cannot cast " + contentObject.getClass() + " to javax.jcr.Node");
        }

        final RenderingEngine renderingEngine = Components.getComponent(RenderingEngine.class);
        final RenderingContext renderingContext = renderingEngine.getRenderingContext();
        ThymeleafComponentElement componentElement = createComponentElement(renderingContext);

        BlossomTemplateDefinition templateDefinition = null;
        try {

            templateDefinition = componentElement.getTemplate(content);
        } catch (Exception x) {

            throw new TemplateProcessingException("Only Blossom, templates supported or template not found", x);
        }

        if (templateDefinition == null) {
            throw new TemplateProcessingException("Template not found");
        }

        Object handlerBean = null;
        String path = templateDefinition.getHandlerPath();
        for (HandlerMetaData meta : templateExporter.getDetectedHandlers().getTemplates()) {

            if (meta.getHandlerPath().equals(path)) {
                handlerBean = meta.getHandler();
                break;
            }

        }
        if (handlerBean == null) {
            throw new TemplateProcessingException("Handler not found");
        }

        HandlerExecutionChain chain = null;
        try {
            for (HandlerMapping hm : this.handlerMappings) {
                HandlerExecutionChain handler = hm.getHandler(request);
                if (handler != null) {
                    chain = handler;
                    break;
                }
            }
        } catch (Exception e) {
            throw new TemplateProcessingException("Cannot find handler", e);
        }
        if (chain == null) {
            throw new TemplateProcessingException("Handler not found " + handlerBean.getClass().getName());
        }
        HandlerAdapter adapter = null;
        for (HandlerAdapter ha : this.handlerAdapters) {

            if (ha.supports(handlerBean)) {
                adapter = ha;
                break;
            }
        }
        if (adapter == null) {
            throw new TemplateProcessingException("Na HandlerAdapter found");

        }
        componentElement.setContent(content);
        Map<String, Object> vars = new HashMap<>();
        vars.put("content", componentElement.getContent());

        ModelAndView mv = null;
        MgnlContext.getWebContext().push(request, response);
        IncludeRequestWrapper wrapper = new IncludeRequestWrapper(request, MgnlContext.getContextPath() + path,
                MgnlContext.getContextPath(), path, null, request.getQueryString());
        for (String key : vars.keySet()) {
            wrapper.setSpecialAttribute(key, vars.get(key));
        }
        try {
            mv = adapter.handle(wrapper, response, handlerBean);
        } catch (Exception e) {
            throw new TemplateProcessingException("Spring handler error", e);
        } finally {
            MgnlContext.getWebContext().pop();
        }
        String template = mv.getViewName();

        String comment = componentElement.createComment();

        Comment commentNode = new Comment(comment);

        doRecursiveProcessing(arguments, element, attributeName, template, mv.getViewName(), commentNode, vars,
                templateDefinition.getTitle(), " /cms:component");
        return ProcessorResult.OK;
    }

}
