import java.util.HashMap;
import java.util.Map.Entry;
import java.util.List;
import java.util.ArrayList;


public abstract class AST{
    public void error(String msg){
        System.err.println(msg);
        System.exit(-1);
    }
};




/* Expressions are similar to arithmetic expressions in the impl
  language: the atomic expressions are just Signal (similar to
  variables in expressions) and they can be composed to larger
  expressions with And (Conjunction), Or (Disjunction), and Not
  (Negation). Moreover, an expression can be using any of the
  functions defined in the definitions. */


abstract class Expr extends AST{
    abstract public boolean eval(Environment env);
}


class Conjunction extends Expr{
    // Example: Signal1 * Signal2
    Expr e1,e2;
    Conjunction(Expr e1,Expr e2){this.e1=e1; this.e2=e2;}
    @Override
    public boolean eval(Environment env) {
        return e1.eval(env) && e2.eval(env);
    }
}


class Disjunction extends Expr{
    // Example: Signal1 + Signal2
    Expr e1,e2;
    Disjunction(Expr e1,Expr e2){this.e1=e1; this.e2=e2;}
    @Override
    public boolean eval(Environment env) {
        return e1.eval(env) || e2.eval(env);
    }
}


class Negation extends Expr{
    // Example: /Signal
    Expr e;
    Negation(Expr e){this.e=e;}
    @Override
    public boolean eval(Environment env) {
        return !e.eval(env);
    }
}


class UseDef extends Expr{
    // Using any of the functions defined by "def"
    // e.g. xor(Signal1,/Signal2)
    String f;  // the name of the function, e.g. "xor"
    List<Expr> args;  // arguments, e.g. [Signal1, /Signal2]
    UseDef(String f, List<Expr> args){
        this.f=f; this.args=args;
    }
    @Override
    public boolean eval(Environment env) {
        // TODO Auto-generated method stub
        Environment nenv = new Environment(env);
        Def definition = env.getDef(f);
        int n = definition.args.size();
        for(int i=0; i<n; i++)
            nenv.setVariable(definition.args.get(i),
                    args.get(i).eval(env));
        return definition.e.eval(nenv);
    }
}


class Signal extends Expr{
    String varname; // a signal is just identified by a name
    Signal(String varname){this.varname=varname;}
    @Override
    public boolean eval(Environment env) {
        if(!env.hasVariable(varname)){
            error("Not difind");
        }
        return env.getVariable(varname);
    }
}


class Def extends AST{
    // Definition of a function
    // Example: def xor(A,B) = A * /B + /A * B
    String f; // function name, e.g. "xor"
    List<String> args;  // formal arguments, e.g. [A,B]
    Expr e;  // body of the definition, e.g. A * /B + /A * B
    Def(String f, List<String> args, Expr e){
        this.f=f; this.args=args; this.e=e;
    }
}


// An Update is any of the lines " signal = expression "
// in the update section


class Update extends AST{
    // Example Signal1 = /Signal2
    String name;  // Signal being updated, e.g. "Signal1"
    Expr e;  // The value it receives, e.g., "/Signal2"
    Update(String name, Expr e){this.e=e; this.name=name;}
    public void eval(Environment env) {
        boolean result = e.eval(env);  // Evaluate the expression `e` in the current environment
        env.setVariable(name, result);  // Set `name` to the evaluated result in `env`

    }
}


/* A Trace is a signal and an array of Booleans, for instance each
  line of the .simulate section that specifies the traces for the
  input signals of the circuit. It is suggested to use this class
  also for the output signals of the circuit in the second
  assignment.
*/


class Trace extends AST{
    // Example Signal = 0101010
    String signal;
    Boolean[] values;
    Trace(String signal, Boolean[] values){
        this.signal=signal;
        this.values=values;
    }

    public String toString() {
        StringBuilder output = new StringBuilder();

        for (Boolean value : values) {
           if (value == null){
               output.append(2);
            }
            else output.append(value ? "1" : "0");  // Append "1" for true and "0" for false
        }
        output.append(" = " + signal);

        return output.toString();
    }
}


/* The main data structure of this simulator: the entire circuit with
  its inputs, outputs, latches, definitions and updates. Additionally
  for each input signal, it has a Trace as simulation input.

  There are two variables that are not part of the abstract syntax
  and thus not initialized by the constructor (so far): simoutputs
  and simlength. It is suggested to use these two variables for
  assignment 2 as follows:


  1. all siminputs should have the same length (this is part of the
  checks that you should implement). set simlength to this length: it
  is the number of simulation cycles that the interpreter should run.


  2. use the simoutputs to store the value of the output signals in
  each simulation cycle, so they can be displayed at the end. These
  traces should also finally have the length simlength.
*/


class Circuit extends AST {
    String name;
    List<String> inputs;
    List<String> outputs;
    List<String> latches;
    List<Def> definitions;
    List<Update> updates;
    List<Trace> siminputs;
    List<Trace> simoutputs;
    int simlength;

    Circuit(String name,
            List<String> inputs,
            List<String> outputs,
            List<String> latches,
            List<Def> definitions,
            List<Update> updates,
            List<Trace> siminputs) {
        this.name = name;
        this.inputs = inputs;
        this.outputs = outputs;
        this.latches = latches;
        this.definitions = definitions;
        this.updates = updates;
        this.siminputs = siminputs;
    }

    public void latchesInit(Environment env) {
        for (String latch : latches) {
            String latchOutput = latch + "'";
            env.setVariable(latchOutput, false);
        }
    }

    public void latchesUpdate(Environment env) {
        for (String latch : latches) {
            String latchOutput = latch + "'";
            boolean latchInputValue = env.getVariable(latch);
            env.setVariable(latchOutput, latchInputValue);
        }
    }

    public void updateOutput(int number, int value, Boolean bol){
        simoutputs.get(number).values[value] = bol;
    }

    public void initialize(Environment env) {
        simoutputs = new ArrayList<>();
        for (String out: outputs){
            simoutputs.add(new Trace(out, new Boolean[siminputs.get(0).values.length]));
        }
        // Step 1: Initialize input signals at time point 0
        for (int i = 0; i < inputs.size(); i++) {
            Trace trace = siminputs.get(i);

            if (trace == null || trace.values.length == 0) {
                throw new IllegalArgumentException("Error: Missing or empty siminput for input signal " + inputs.get(i));
            }

            env.setVariable(inputs.get(i), trace.values[0]);
        }

        // Step 2: Initialize all latch outputs to 0
        latchesInit(env);

        // Step 3: Initialize all remaining signals using each Update
        for (Update update : updates) {
            update.eval(env);
        }

        for (int i = 0; i<outputs.size(); i++){
            updateOutput(i, 0, env.getVariable(outputs.get(i)));
        }

        // Step 4: Print the environment to show all variables and their values
        System.out.println(env.toString());
    }
    public void nextCycle(Environment env, int i){
        // Step 1: Initialize input signals at time point n
        for (int n = 0; n<inputs.size(); n++) {
            Trace trace = siminputs.get(n);

            if (trace == null || trace.values.length == 0) {
                throw new IllegalArgumentException("Error: Missing or empty siminput for input signal " + inputs.get(n));
            }

            env.setVariable(inputs.get(n), trace.values[i]);
        }

        latchesUpdate(env);

        for (Update update: updates){
            update.eval(env);
        }

        for (int n = 0; n<outputs.size(); n++){
            updateOutput(n, i, env.getVariable(outputs.get(n)));
        }

        System.out.println(env.toString());

    }

    public void runSimulator(){
        Environment env = new Environment(definitions);
        initialize(env);

        for(int n = 1; n < siminputs.get(0).values.length; n++){
            nextCycle(env, n);
        }

        for (Trace t: siminputs){
            System.out.println(t.toString());
        }
        for (Trace t: simoutputs){
            System.out.println(t.toString());
        }
    }


}
