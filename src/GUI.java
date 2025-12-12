import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GUI {
    private JLabel _headerLabel;
    private JTextArea _textArea;
    private JFormattedTextField _costPerM3;
    private JFrame _frame;
    private static Map<String, SimpleSolver.PriceCalculator> PRICE_CALCULATOR_MAP = new HashMap<>();
    private static Map<String, Integer> SYSTEM_MAP = new HashMap<>();
    private JComboBox<String> buyOptionDropdown;
    private JComboBox<String> systemSelectorDropdown;

    static {
        PRICE_CALCULATOR_MAP.put("Buy 5%", SimpleSolver.PriceCalculator.BUY());
        PRICE_CALCULATOR_MAP.put("Sell 5%", SimpleSolver.PriceCalculator.SELL());
        PRICE_CALCULATOR_MAP.put("Max Buy", SimpleSolver.PriceCalculator.MAX_BUY());
        PRICE_CALCULATOR_MAP.put("Min Sell", SimpleSolver.PriceCalculator.MIN_SELL());
        SYSTEM_MAP.put("The Forge", 10000002);
        SYSTEM_MAP.put("Domain", 10000043);
        SYSTEM_MAP.put("Hek", 10000030);
    }


    static void main() throws Exception {
        try {
            Cache.initialize();
            new GUI().run();
        } finally {
            Cache.save();
        }
    }

    private JButton createButton(Container frame, String title) {
        JButton button = new JButton(title);
        button.setSize(100, 20);
        frame.add(button);
        return button;
    }

    public void run() throws Exception {
        // Creating instance of JFrame
        _frame = new JFrame();
        _frame.setLayout(new BoxLayout(_frame.getContentPane(), BoxLayout.Y_AXIS));


        List<Integer> resources = new ArrayList<>();
        resources.add(34);
        resources.add(35);
        resources.add(36);
        resources.add(37);
        resources.add(38);
        resources.add(39);
        resources.add(40);

        StringBuilder sb = new StringBuilder();
        for (int i : resources) {
            sb.append(Cache.getItemName(i)).append("\n");
        }

        _headerLabel = new JLabel("Paste your inputs here:");
        _frame.add(_headerLabel);
        _frame.setResizable(true);
        _textArea = new JTextArea();
        _textArea.setText(sb.toString());
        _textArea.setMaximumSize(new Dimension(600, 400));
        _textArea.setLineWrap(false);
        JScrollPane areaScrollPane = new JScrollPane(_textArea);
        areaScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        _frame.getContentPane().add(areaScrollPane);


        JPanel buttonPanel = new JPanel();
        buttonPanel.setMaximumSize(new Dimension(3000, 50));
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        JLabel costPerM3Label = new JLabel("Cost Per m3: ");
        _costPerM3 = new JFormattedTextField();
        _costPerM3.setText("450");
        _costPerM3.setMaximumSize(new Dimension(100, 30));
        ((PlainDocument) _costPerM3.getDocument()).setDocumentFilter(new MyIntFilter());
        buttonPanel.add(costPerM3Label);
        buttonPanel.add(_costPerM3);

        JButton calculateButton = createButton(buttonPanel, "Calculate");
        calculateButton.addActionListener(e -> {
            try {
                calculate();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        String[] buyChoices = PRICE_CALCULATOR_MAP.keySet().stream().sorted().toList().toArray(new String[0]);
        buyOptionDropdown = new JComboBox<>(buyChoices);
        buttonPanel.add(buyOptionDropdown);

        String[] systemChoices = SYSTEM_MAP.keySet().stream().sorted().toList().toArray(new String[0]);
        systemSelectorDropdown = new JComboBox<>(systemChoices);
        buttonPanel.add(systemSelectorDropdown);

        // Creating instance of JButton
        JButton clearCacheButton = createButton(buttonPanel, "Clear Cache");
        clearCacheButton.addActionListener(e -> clearCache());

        JButton saveCacheButton = createButton(buttonPanel, "Save Cache");
        saveCacheButton.addActionListener(e -> {
            try {
                Cache.save();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        _frame.add(buttonPanel);

        // 400 width and 500 height
        _frame.setSize(800, 800);

        // making the frame visible
        _frame.setVisible(true);
    }

    public void clearCache() {
        // TODO
    }

    public void calculate() throws Exception {
        List<String> errors = doCalculate();
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String error : errors) {
                System.out.println(error);
                sb.append(error).append("\n");
            }
            JOptionPane.showMessageDialog(_frame, sb.toString());
        }
    }

    private List<String> doCalculate() throws Exception {

        String s = _textArea.getText();
        List<String> errors = new ArrayList<>();
        Pattern p = Pattern.compile("\\([^)]*\\)");
        List<Integer> ids = new ArrayList<>();
        for (String line : s.split("\n")) {
            String resource;
            line = line.strip();
            try {
                Matcher m = p.matcher(line);
                resource = m.replaceAll("\t").strip().split("\t", 2)[0]; // TODO: Smart parsing
            } catch (Exception e) {
                errors.add("Unable to parse '" + line + "'");
                continue;
            }
            if (resource.isEmpty()) {
                continue;
            }

            try {
                int i = Cache.getItemId(resource);
                ids.add(i);
            } catch (Exception e) {
                errors.add("Unable to find id for '" + resource + "'");
            }

        }

        System.out.println("ids.size():" + ids.size());

        SimpleSolver solver = new SimpleSolver();

        StringBuilder sb = new StringBuilder();
        for (String string : solver.solve(ids, SYSTEM_MAP.get(systemSelectorDropdown.getSelectedItem()), 0.5f, Integer.parseInt(_costPerM3.getText()), PRICE_CALCULATOR_MAP.get(buyOptionDropdown.getSelectedItem()))) {
            sb.append(string);
        }

        _textArea.setText(sb.toString());


        return errors;

    }


    class MyIntFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {

            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getText(0, doc.getLength()));
            sb.insert(offset, string);

            if (test(sb.toString())) {
                super.insertString(fb, offset, string, attr);
            } else {
                // warn the user and don't allow the insert
            }
        }

        private boolean test(String text) {
            try {
                Integer.parseInt(text);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {

            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getText(0, doc.getLength()));
            sb.replace(offset, offset + length, text);

            if (test(sb.toString())) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                // warn the user and don't allow the insert
            }

        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getText(0, doc.getLength()));
            sb.delete(offset, offset + length);

            if (test(sb.toString())) {
                super.remove(fb, offset, length);
            } else {
                // warn the user and don't allow the insert
            }

        }
    }
}
