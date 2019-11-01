/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.timestamper.pipeline;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleAnnotatorFactory;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.plugins.timestamper.Timestamp;
import hudson.plugins.timestamper.format.TimestampFormat;
import hudson.plugins.timestamper.format.TimestampFormatProvider;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Interprets marks added by {@link GlobalDecorator}.
 */
public final class GlobalAnnotator extends ConsoleAnnotator<Object> {

    private static final long serialVersionUID = 1;

    private static final Logger LOGGER = Logger.getLogger(GlobalAnnotator.class.getName());

    @Override
    public ConsoleAnnotator<Object> annotate(Object context, MarkupText text) {
        Run<?, ?> build;
        if (context instanceof Run) {
            build = (Run<?, ?>) context;
        } else if (context instanceof FlowNode) {
            FlowExecutionOwner owner = ((FlowNode) context).getExecution().getOwner();
            if (owner == null) {
                return null;
            }
            Queue.Executable executable;
            try {
                executable = owner.getExecutable();
            } catch (IOException x) {
                LOGGER.log(Level.FINE, null, x);
                return null;
            }
            if (executable instanceof Run) {
                build = (Run) executable;
            } else {
                return null;
            }
        } else {
            return null;
        }
        long buildStartTime = build.getStartTimeInMillis();
        String html = text.toString(true);
        int start = 0;
        // cf. LogStorage.startStep
        if (html.startsWith("<span class=\"pipeline-new-node\" ", start)) {
            start = html.indexOf('>', start) + 1;
        }
        // cf. AnsiHtmlOutputStream.setForegroundColor
        if (html.startsWith("<span style=\"color", start)) {
            start = html.indexOf('>', start) + 1;
        }
        // Generic wrapper style that any other ConsoleAnnotator can use
        // to avoid conflict with Timestamper's detection.
        if (html.startsWith("<span data-timestamper", start)) {
            start = html.indexOf('>', start) + 1;
        }
        if (html.startsWith("[", start)) {
            int end = html.indexOf(']', start);
            if (end != -1) {
                try {
                    long millisSinceEpoch = ZonedDateTime.parse(html.substring(start + 1, end), GlobalDecorator.UTC_MILLIS).toInstant().toEpochMilli();
                    // Alternately: Instant.parse(html.substring(start + 1, end)).toEpochMilli()
                    Timestamp timestamp = new Timestamp(millisSinceEpoch - buildStartTime, millisSinceEpoch);
                    TimestampFormat format = TimestampFormatProvider.get();
                    format.markup(text, timestamp);
                    text.addMarkup(0, 26, "<span style=\"display: none\">", "</span>");
                } catch (DateTimeParseException x) {
                    // something else, ignore
                }
            }
        }
        return this;
    }

    @Extension
    public static final class Factory extends ConsoleAnnotatorFactory<Object> {

        @Override
        public ConsoleAnnotator<Object> newInstance(Object context) {
            if (context instanceof Run && context instanceof FlowExecutionOwner.Executable) {
                return new GlobalAnnotator();
            } else if (context instanceof FlowNode) {
                return new GlobalAnnotator();
            }
            // Note that prior to 2.145, we actually get FlowNode.class here rather than a FlowNode, so there is no per-step annotation.
            return null;
        }
    }

}
