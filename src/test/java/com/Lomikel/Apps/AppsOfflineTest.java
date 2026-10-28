package com.Lomikel.Apps;

import org.apache.commons.cli.CommandLine;
import com.Lomikel.Utils.Init;
import groovy.lang.Binding;

/** Offline regression checks for the public command-line argument contract. */
public final class AppsOfflineTest {
  public static void main(String[] unused) {
    CommandLine malformed = CLI.parseArgs(new String[] {"--args", "missingValue"}, "LUC");
    if (malformed != null) throw new AssertionError("Malformed script arguments must be rejected");
    CommandLine trailing = CLI.parseArgs(new String[] {"--args", "ok=1,"}, "LUC");
    if (trailing != null || !CLI.arguments().isEmpty())
      throw new AssertionError("Trailing empty argument must be rejected");
    CommandLine partial = CLI.parseArgs(new String[] {"--batch", "--source", "bad.py",
                                                    "--args", "valid=1,missingValue"}, "LUC");
    if (partial != null || !CLI.arguments().isEmpty() || CLI.batch() || CLI.source() != null)
      throw new AssertionError("Rejected argument list must not expose partial options");
    CommandLine first = CLI.parseArgs(new String[] {"--args", "url='a=b'", "--source", "example.py", "--batch"}, "LUC");
    if (first == null || !"'a=b'".equals(CLI.argument("url")) || !CLI.batch() ||
        !"python".equals(CLI.api())) throw new AssertionError("First invocation was not parsed");
    CommandLine second = CLI.parseArgs(new String[0], "LUC");
    if (second == null || CLI.batch() || CLI.source() != null || CLI.argument("url") != null ||
        !"groovy".equals(CLI.api())) throw new AssertionError("A second invocation inherited stale options");
    CommandLine directHelp = CLI.parseArgs(new String[] {"--help", "--batch", "--args", "ok=1"}, "LUC");
    if (directHelp != null || CLI.batch() || !CLI.arguments().isEmpty())
      throw new AssertionError("Help request exposed executable options");
    Init._initialised = true;  // Do not perform the optional version notification in an offline test.
    String help = LUC.doit(new String[] {"--help", "--api", "unsupported"});
    if (!"".equals(help) || LUC.cli() != null)
      throw new AssertionError("Help request must not create a client or validate a language");
    CLI.parseArgs(new String[] {"--args", "answer=41"}, "LUC");
    Binding binding = new Binding();
    GCLI._sharedData = binding;
    GCLI._shell = null;
    GCLI._sh = new GShell(binding).shell();
    new GCLI(null, null).setupShell();
    if (!Integer.valueOf(41).equals(binding.getVariable("answer")))
      throw new AssertionError("Interactive Groovy shell lost the -o argument");
    CLI.parseArgs(new String[] {"--api", "python", "--batch", "--args", "answer=41"}, "LUC");
    PYCLI py = new PYCLI(null, null);
    String pythonResult = py.execute();
    if (!"43".equals(pythonResult) ||
        !"41".equals(py.interpreter().eval("answer").toString()) ||
        !"42".equals(py.interpreter().eval("loaded").toString()))
      throw new AssertionError("Python CLI lost arguments or did not execute multiline init.py");
    py.close();
    CLI.parseArgs(new String[] {"--api", "python", "--batch"}, "LUC");
    PYCLI embedded = new PYCLI("embedded.py", null);
    embedded.execute();
    if (!"7".equals(embedded.interpreter().eval("embedded").toString()))
      throw new AssertionError("Embedded Python statement script was not executed");
    embedded.close();
    CLI.setProfile("profile-expression");
    CLI.parseArgs(new String[] {"--api", "python", "--batch"}, "LUC");
    PYCLI expressions = new PYCLI("expression.py", null);
    String expressionResult = expressions.execute();
    if (!"527".equals(expressionResult))
      throw new AssertionError("Python profile, state and embedded expression results changed: " + expressionResult);
    expressions.close();
    CLI.setProfile(null);
    CLI.parseArgs(new String[] {"--api", "groovy", "--batch"}, "LUC");
    GCLI groovy = new GCLI("embedded.groovy", null);
    groovy.execute();
    if (!Integer.valueOf(7).equals(groovy.shell().getContext().getVariable("groovyEmbedded")))
      throw new AssertionError("Embedded Groovy statement script was not executed");
    groovy.close();
    System.out.println("AppsOfflineTest PASS");
  }
}
