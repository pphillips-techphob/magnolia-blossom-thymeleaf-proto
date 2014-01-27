package thymeleaf.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.springframework.context.ApplicationContext;
import org.thymeleaf.Arguments;
import org.thymeleaf.dom.Comment;
import org.thymeleaf.dom.Element;
import org.thymeleaf.dom.Node;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.fragment.FragmentAndTarget;
import org.thymeleaf.processor.ProcessorResult;
import org.thymeleaf.processor.attr.AbstractAttrProcessor;
import org.thymeleaf.standard.fragment.StandardFragmentProcessor;
import org.thymeleaf.standard.processor.attr.StandardFragmentAttrProcessor;

/**
 * Created with IntelliJ IDEA. User: tkratz Date: 11.11.12 Time: 09:39 To change this template use File | Settings |
 * File Templates.
 */
public abstract class AbstractRecursiveInclusionProcessor extends AbstractAttrProcessor {

    private static final String FRAGMENT_ATTR_NAME = StandardFragmentAttrProcessor.ATTR_NAME;

    protected ServletContext servletContext;
    protected ApplicationContext context;

    public AbstractRecursiveInclusionProcessor(ApplicationContext ctx, ServletContext sctx, String attrName) {
        super(attrName);
        this.context = ctx;
        this.servletContext = sctx;
    }

    protected ProcessorResult doRecursiveProcessing(Arguments arguments, Element element, String attributeName,
            String fragmentSpec, String template, Comment commentNode, Map<String, Object> vars, String documentName,
            String closeTag) {
        // final IWebContext webcontext =
        // new SpringWebContext(MgnlContext.getWebContext().getRequest(), MgnlContext.getWebContext().getResponse(),
        // servletContext , MgnlContext.getWebContext().getRequest().getLocale(), vars, this.context);

        final FragmentAndTarget fragmentAndTarget = getFragmentAndTarget(arguments, element, attributeName, template,
                true);

        final List<Node> fragmentNodes = fragmentAndTarget.extractFragment(arguments.getConfiguration(), arguments,
                arguments.getTemplateRepository());

        if (fragmentNodes == null) {
            throw new TemplateProcessingException("Cannot correctly process \"" + attributeName + "\" attribute. "
                    + "Fragment specification \"" + fragmentSpec + "\" matched null.");
        }
        // process fragment nodes

        element.clearChildren();
        element.removeAttribute(attributeName);

        List<Node> newNodes = new ArrayList<>();
        newNodes.add(commentNode);
        newNodes.addAll(fragmentNodes);

        commentNode = new Comment(closeTag);
        newNodes.add(commentNode);

        element.setChildren(newNodes);
        element.getParent().extractChild(element);

        for (Node node : fragmentNodes) { // workaround: local vars don't get set on the replacement node
            node.setAllNodeLocalVariables(vars);
        }
        return ProcessorResult.setLocalVariables(vars);
    }

    protected final FragmentAndTarget getFragmentAndTarget(final Arguments arguments, final Element element,
            final String attributeName, final String attributeValue, final boolean substituteInclusionNode) {

        final String targetAttributeName = getTargetAttributeName(arguments, element, attributeName, attributeValue);

        return StandardFragmentProcessor.computeStandardFragmentSpec(arguments.getConfiguration(), arguments,
                attributeValue, null, targetAttributeName, !substituteInclusionNode);

    }

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

}
