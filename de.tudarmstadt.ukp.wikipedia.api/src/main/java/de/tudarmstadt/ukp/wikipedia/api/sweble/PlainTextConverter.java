/*******************************************************************************
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package de.tudarmstadt.ukp.wikipedia.api.sweble;

/**
 * Derived from the TextConverter class which was published in the
 * Sweble example project provided on
 * http://http://sweble.org by the Open Source Research Group,
 * University of Erlangen-Nürnberg under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0)
 */

import de.fau.cs.osr.ptk.common.AstVisitor;
import de.fau.cs.osr.ptk.common.ast.AstText;
import de.fau.cs.osr.utils.StringTools;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.*;
import org.sweble.wikitext.parser.parser.LinkTargetException;

import java.util.LinkedList;
import java.util.regex.Pattern;

/**
 * A visitor to convert an article AST into a plain text representation. To
 * better understand the visitor pattern as implemented by the Visitor class,
 * please take a look at the following resources:
 * <ul>
 * <li><a href="http://en.wikipedia.org/wiki/Visitor_pattern">Visitor Pattern (classic pattern)</a></li>
 * <li><a href="http://www.javaworld.com/javaworld/javatips/jw-javatip98.html">the version we use here</a></li>
 * </ul>
 *
 * The methods needed to descend into an AST and visit the children of a given
 * node <code>n</code> are
 * <ul>
 * <li><code>dispatch(n)</code> - visit node <code>n</code>,</li>
 * <li><code>iterate(n)</code> - visit the <b>children</b> of node
 * <code>n</code>,</li>
 * <li><code>map(n)</code> - visit the <b>children</b> of node <code>n</code>
 * and gather the return values of the <code>visit()</code> calls in a list,</li>
 * <li><code>mapInPlace(n)</code> - visit the <b>children</b> of node
 * <code>n</code> and replace each child node <code>c</code> with the return
 * value of the call to <code>visit(c)</code>.</li>
 * </ul>
 *
 * @author Open Source Research Group, University of Erlangen-Nürnberg
 */
public class PlainTextConverter extends AstVisitor<WtNode>
{

	private static final Pattern ws = Pattern.compile("\\s+");

	private final Log logger = LogFactory.getLog(getClass());

	private final WikiConfig config;

	private final int wrapCol;

	private StringBuilder sb;

	private StringBuilder line;

	private boolean pastBod;

	private int needNewlines;

	private boolean needSpace;

	private boolean noWrap;
	private boolean enumerateSections;

	private LinkedList<Integer> sections;

	// =========================================================================


	/**
	 * Creates a new visitor that produces a plain text String representation
	 * of a parsed Wikipedia article.
s	 */
	public PlainTextConverter()
	{
		this(DefaultConfigEnWp.generate(), false, Integer.MAX_VALUE); //no fixed textwidth
	}

	/**
	 * Creates a new visitor that produces a plain text String representation
	 * of a parsed Wikipedia article.
	 *
	 * @param enumerateSection {@code True}, if sections should be enumerated in the output, {@code false} otherwise.
	 */
	public PlainTextConverter(boolean enumerateSection)
	{
		this(DefaultConfigEnWp.generate(), enumerateSection, Integer.MAX_VALUE); //no fixed textwidth
	}

	/**
	 * Creates a new visitor that produces a plain text String representation
	 * of a parsed Wikipedia article.
	 *
	 * @param config A valid {@link WikiConfig} instance. Must not be {@code null}.
	 * @param enumerateSections {@code True}, if sections should be enumerated in the output, {@code false} otherwise.
	 * @param wrapCol Defines the max length of a line. longer lines will be broken.
	 */
	public PlainTextConverter(WikiConfig config, boolean enumerateSections, int wrapCol)
	{
		this.config = config;
		this.wrapCol = wrapCol;
		this.enumerateSections = enumerateSections;
	}

	@Override
	protected WtNode before(WtNode node)
	{
		// This method is called by go() before visitation starts
		sb = new StringBuilder();
		line = new StringBuilder();
		pastBod = false;
		needNewlines = 0;
		needSpace = false;
		noWrap = false;
		sections = new LinkedList<Integer>();
		return super.before(node);
	}

	@Override
	protected Object after(WtNode node, Object result)
	{
		finishLine();

		// This method is called by go() after visitation has finished
		// The return value will be passed to go() which passes it to the caller
		return sb.toString();
	}

	// =========================================================================

	/*
	 * We CAN NOT allow this method being implemented here, as it will clash with
	 * 'visit(de.fau.cs.osr.ptk.common.ast.AstText)' otherwise at runtime.
	 * Thus, we are ignoring it for now. (see #160)
	 *
	public void visit(WtNode n)
	{
		// Fallback for all nodes that are not explicitly handled below
//		write("<");
//		write(n.getNodeName());
//		write(" />");
	}
	*/

	public void visit(WtNodeList n)
	{
		iterate(n);
	}

	public void visit(WtPage p)
	{
		iterate(p);
	}

	public void visit(AstText text)
	{
		write(text.getContent());
	}

	public void visit(WtWhitespace w)
	{
		write(" ");
	}

	public void visit(WtBold b)
	{
		//write("**");
		iterate(b);
		//write("**");
	}

	public void visit(WtItalics i)
	{
		//write("//");
		iterate(i);
		//write("//");
	}

	public void visit(WtXmlCharRef cr)
	{
		write(Character.toChars(cr.getCodePoint()));
	}

	public void visit(WtXmlEntityRef er)
	{

		String ch = er.getResolved();
		if (ch == null)
		{
			write('&');
			write(er.getName());
			write(';');
		}
		else
		{
			write(ch);
		}
	}

	public void visit(WtUrl url)
	{
		write(url.getProtocol());
		write(':');
		write(url.getPath());
	}

	public void visit(WtExternalLink link)
	{
		//TODO How should we represent external links in the plain text output?
		write('[');
		iterate(link.getTitle());
		write(']');
	}

	public void visit(WtInternalLink link)
	{
		try
		{
			PageTitle page = PageTitle.make(config, link.getTarget().getAsString());
			if (page.getNamespace().equals(config.getNamespace("Category"))) {
				return;
			}
		}
		catch (LinkTargetException e)
		{
			logger.warn(e.getLocalizedMessage());
		}

		write(link.getPrefix());
		WtLinkTitle pageTitle = link.getTitle();

		if (pageTitle == null || pageTitle.isEmpty())
		{
			write(link.getTarget().getAsString());
		}
		else
		{
			iterate(link.getTitle());
		}
		write(link.getPostfix());
	}

	public void visit(WtSection s)
	{
		finishLine();
		StringBuilder saveSb = sb;
		boolean saveNoWrap = noWrap;

		sb = new StringBuilder();
		noWrap = true;

		iterate(s.getHeading());
		finishLine();
		String title = sb.toString().trim();

		sb = saveSb;

		if (s.getLevel() >= 1)
		{
			while (sections.size() > s.getLevel()) {
				sections.removeLast();
			}
			while (sections.size() < s.getLevel()) {
				sections.add(1);
			}

			if(enumerateSections){
				StringBuilder sb2 = new StringBuilder();
				for (int i = 0; i < sections.size(); ++i)
				{
					if (i < 1) {
						continue;
					}

					sb2.append(sections.get(i));
					sb2.append('.');
				}

				if (sb2.length() > 0) {
					sb2.append(' ');
				}
				sb2.append(title);
				title = sb2.toString();
			}
		}

		newline(1);
		write(title);
		newline(1);
//		write(StringUtils.strrep('-', title.length()));
//		newline(1);

		noWrap = saveNoWrap;

		iterate(s.getBody());

		while (sections.size() > s.getLevel()) {
			sections.removeLast();
		}
		sections.add(sections.removeLast() + 1);
	}

	public void visit(WtParagraph p)
	{
		iterate(p);
		newline(1);
	}

	public void visit(WtHorizontalRule hr)
	{
		newline(1);
//		write(StringUtils.strrep('-', wrapCol));
//		newline(1);
	}

	public void visit(WtXmlElement e)
	{
		if (e.getName().equalsIgnoreCase("br"))
		{
			newline(1);
		}
		else
		{
			iterate(e.getBody());
		}
	}

	public void visit(WtListItem n)
	{
		iterate(n);
	}


	// =========================================================================
	// Stuff we want to hide

	public void visit(WtImageLink n)
	{
	}

	public void visit(WtIllegalCodePoint n)
	{
	}

	public void visit(WtXmlComment n)
	{
	}

	public void visit(WtTemplate n)
	{
	}

	public void visit(WtTemplateArgument n)
	{
	}

	public void visit(WtTemplateParameter n)
	{
	}

	public void visit(WtTagExtension n)
	{
	}


	// =========================================================================

	private void newline(int num)
	{
		if (pastBod)
		{
			if (num > needNewlines) {
				needNewlines = num;
			}
		}
	}

	private void wantSpace()
	{
		if (pastBod) {
			needSpace = true;
		}
	}

	private void finishLine()
	{
		sb.append(line.toString());
		line.setLength(0);
	}

	private void writeNewlines(int num)
	{
		finishLine();
		sb.append(StringTools.strrep('\n', num));
		needNewlines = 0;
		needSpace = false;
	}

	private void writeWord(String s)
	{
		int length = s.length();
		if (length == 0) {
			return;
		}

		if (!noWrap && needNewlines <= 0)
		{
			if (needSpace) {
				length += 1;
			}

			if (line.length() + length >= wrapCol && line.length() > 0) {
				writeNewlines(1);
			}
		}

		if (needSpace && needNewlines <= 0) {
			line.append(' ');
		}

		if (needNewlines > 0) {
			writeNewlines(needNewlines);
		}

		needSpace = false;
		pastBod = true;
		line.append(s);
	}

	private void write(String s)
	{
		if (s.isEmpty()) {
			return;
		}

		if (Character.isSpaceChar(s.charAt(0))) {
			wantSpace();
		}

		String[] words = ws.split(s);
		for (int i = 0; i < words.length;)
		{
			writeWord(words[i]);
			if (++i < words.length) {
				wantSpace();
			}
		}

		char charAtEnd = s.charAt(s.length() - 1);
		if('\n' == charAtEnd){
			writeNewlines(1);
		}
		if (Character.isSpaceChar(charAtEnd)) {
			wantSpace();
		}
	}

	private void write(char[] cs)
	{
		write(String.valueOf(cs));
	}

	private void write(char ch)
	{
		writeWord(String.valueOf(ch));
	}

	private void write(int num)
	{
		writeWord(String.valueOf(num));
	}
}
