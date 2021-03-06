/*
 * @author Marty Stepp
 * @version 2014/11/15
 * - spinner GIF while tests are in progress
 * - shut down JBE if window closed while tests are in progress
 */

package stanford.spl;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import stanford.cs106.diff.DiffGui;
import stanford.cs106.gui.*;

public class AutograderUnitTestGUI extends Observable implements ActionListener, MouseListener {
	private static final int DIALOG_WIDTH = 500;   // px
	private static final Color ZEBRA_STRIPE_COLOR_1 = new Color(250, 250, 250);
	private static final Color ZEBRA_STRIPE_COLOR_2 = new Color(235, 235, 235);
	
	// should possibly keep in sync with colors in DiffGui.java
	private static final Color PASS_COLOR = new Color(0, 96, 0);
	private static final Color FAIL_COLOR = new Color(96, 0, 0);
	private static final Color WARN_COLOR = new Color(112, 112, 0);
	private static Color NORMAL_COLOR = null;
	
	//private static final int MIN_WIDTH = 75;
	private static AutograderUnitTestGUI instance;             // singleton
	private static AutograderUnitTestGUI styleCheckInstance;   // singleton
	
	private static final String TESTS_TITLE = "Autograder Tests";
	private static final String STYLE_CHECK_TITLE = "Style Checker";
	
	public static synchronized AutograderUnitTestGUI getInstance(JavaBackEnd javaBackEnd) {
		if (instance == null) {
			instance = new AutograderUnitTestGUI(javaBackEnd, TESTS_TITLE);
		}
		return instance;
	}
	
	public static synchronized AutograderUnitTestGUI getInstance(JavaBackEnd javaBackEnd, boolean isStyleCheck) {
		if (isStyleCheck) {
			return getStyleCheckInstance(javaBackEnd);
		} else {
			return getInstance(javaBackEnd);
		}
	}
	
	public static synchronized AutograderUnitTestGUI getStyleCheckInstance(JavaBackEnd javaBackEnd) {
		if (styleCheckInstance == null) {
			styleCheckInstance = new AutograderUnitTestGUI(javaBackEnd, STYLE_CHECK_TITLE);
		}
		return styleCheckInstance;
	}
	
	private class TestInfo {
		private String name;
		private JCheckBox checked;
		private JLabel description;
		private JLabel result;
		private Map<String, String> details = new LinkedHashMap<String, String>();
	}
	
	private JavaBackEnd javaBackEnd;
	private JFrame frame = null;
	private JScrollPane scroll = null;
	private Box contentPaneBox = null;
	private JLabel descriptionLabel = null;
	private JLabel southLabel = null;
	
	private Map<String, Container> allCategories = new LinkedHashMap<String, Container>();
	private Container currentCategory = null;
	private Map<Object, TestInfo> allTests = new LinkedHashMap<Object, TestInfo>();
	
	private int passCount = 0;
	private int testCount = 0;
	private boolean testingIsInProgress = false;
	
	public AutograderUnitTestGUI(JavaBackEnd javaBackEnd, String title) {
		this.javaBackEnd = javaBackEnd;
		frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		frame.setTitle(title);
		frame.setVisible(false);
		frame.addWindowListener(new AutograderUnitTestGUIWindowAdapter());
		
		descriptionLabel = new JLabel("Autograder Tests");
		if (NORMAL_COLOR == null) {
			NORMAL_COLOR= descriptionLabel.getForeground();
		}
		descriptionLabel.setHorizontalAlignment(JLabel.CENTER);
		descriptionLabel.setAlignmentX(0.5f);
		GuiUtils.shrinkFont(descriptionLabel);
		frame.add(descriptionLabel, BorderLayout.NORTH);
		
		contentPaneBox = Box.createVerticalBox();
		scroll = new JScrollPane(contentPaneBox);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(32);
		frame.add(scroll, BorderLayout.CENTER);
		
		southLabel = new JLabel(" ");
		southLabel.setIcon(new ImageIcon("progress.gif"));
		southLabel.setHorizontalTextPosition(SwingConstants.LEFT);
		southLabel.setHorizontalAlignment(JLabel.CENTER);
		southLabel.setAlignmentX(0.5f);
		frame.add(southLabel, BorderLayout.SOUTH);
		updateSouthText();
		
		new WindowCloseKeyListener(frame);
		
		SPLWindowSettings.loadWindowLocation(frame);
		SPLWindowSettings.saveWindowLocation(frame);
	}
	
	public void actionPerformed(ActionEvent event) {
		showTestDetails(event.getActionCommand());
	}
	
	public Container addCategory(String name) {
		if (!allCategories.containsKey(name)) {
			final Container category = Box.createVerticalBox();
			currentCategory = category;
			allCategories.put(name, currentCategory);
			if (!name.isEmpty()) {
				if (isStyleCheck()) {
					TitledBorder border = BorderFactory.createTitledBorder(name);
					border.setTitleJustification(SwingConstants.CENTER);
					((Box) currentCategory).setBorder(border);
				} else {
					Border border = BorderFactory.createLineBorder(Color.DARK_GRAY);
					((Box) currentCategory).setBorder(border);
				}
				currentCategory.setBackground(ZEBRA_STRIPE_COLOR_2);
			}
			
			if (!isStyleCheck()) {
				// top row of 'select/deselect all' buttons
				JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
				JButton selectAll = new JButton("All");
				selectAll.setIcon(new ImageIcon("checkbox-checked.gif"));
				GuiUtils.shrinkFont(selectAll, 2);
				selectAll.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent event) {
						selectAll(category, true);
					}
				});
				JButton deselectAll = new JButton("None");
				deselectAll.setIcon(new ImageIcon("checkbox-unchecked.gif"));
				GuiUtils.shrinkFont(deselectAll, 2);
				deselectAll.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent event) {
						selectAll(category, false);
					}
				});
				top.add(selectAll);
				top.add(deselectAll);
				if (!name.isEmpty()) {
					JLabel nameLabel = new JLabel(name);
					GuiUtils.shrinkFont(nameLabel, 1);
					top.add(nameLabel);
				}
				category.add(top);
			}
			
			contentPaneBox.add(currentCategory);
			checkVisibility();
		}
		return allCategories.get(name);
	}
	
	public void addTest(String testName, String categoryName) {
		testCount++;
		currentCategory = addCategory(categoryName);
		
		// add a panel about this test
		JPanel testPanel = new JPanel(new BorderLayout());
		JPanel testWestPanel = new JPanel();
		Color bgColor = (testCount % 2 == 0) ? ZEBRA_STRIPE_COLOR_1 : ZEBRA_STRIPE_COLOR_2;
		testPanel.setBackground(bgColor);
		testWestPanel.setBackground(bgColor);
		
		TestInfo testInfo = new TestInfo();
		testInfo.name = testName;
		allTests.put(testName, testInfo);
		
		testInfo.checked = new JCheckBox();
		
		// testInfo.checked.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
		// testInfo.checked.setPreferredSize(new Dimension(10, 10));
		
		testInfo.checked.setSelected(true);
		if (!isStyleCheck()) {
			testWestPanel.add(testInfo.checked);
		}
		
		JLabel testCountLabel = new JLabel(String.format("%3d. ", testCount));
		allTests.put(testCountLabel, testInfo);
		testCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		testCountLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		Font oldFont = testCountLabel.getFont();
		testCountLabel.setFont(new Font("Monospaced", Font.BOLD, oldFont.getSize()));
		// boldFont(testCountLabel);
		testWestPanel.add(testCountLabel);
		testPanel.add(testWestPanel, BorderLayout.WEST);
		
		String testNameShort = testName.replaceAll("Test_[0-9]{1,5}_", "");
		testInfo.description = new JLabel(testNameShort);
		testInfo.description.setToolTipText("Click to see detailed results from this test.");
		GuiUtils.shrinkFont(testInfo.description);
		testInfo.description.setFont(testInfo.description.getFont().deriveFont(Font.BOLD));
		testPanel.add(testInfo.description, BorderLayout.CENTER);
		testInfo.description.addMouseListener(this);
		allTests.put(testInfo.description, testInfo);
		
		testInfo.result = new JLabel(new ImageIcon("running.gif"));
		testInfo.result.setText("        ");
		GuiUtils.shrinkFont(testInfo.result, 2);
		testInfo.result.setHorizontalTextPosition(SwingConstants.LEFT);
		testInfo.result.setToolTipText("Click to see detailed results from this test.");
		testInfo.result.addMouseListener(this);
		testPanel.add(testInfo.result, BorderLayout.EAST);
		allTests.put(testInfo.result, testInfo);
		
		currentCategory.add(testPanel);
		
		checkVisibility();
	}
	
	public void clearTests() {
		currentCategory = null;
		allCategories.clear();
		allTests.clear();
		passCount = 0;
		testCount = 0;
		testingIsInProgress = true;
		contentPaneBox.removeAll();
		contentPaneBox.validate();
		scroll.validate();
		updateSouthText();
		checkVisibility();
	}
	
	public void clearTestResults() {
		passCount = 0;
		for (TestInfo testInfo : allTests.values()) {
			testInfo.details.clear();
			testInfo.description.setForeground(NORMAL_COLOR);
			testInfo.result.setIcon(new ImageIcon("running.gif"));
			testInfo.result.setText("");
		}
		updateSouthText();
	}
	
	private String getTestResult(String testName) {
		TestInfo testInfo = allTests.get(testName);
		if (testInfo == null) {
			return "no such test";
		} else if (testInfo.description.getForeground().equals(FAIL_COLOR)) {
			return "fail";
		} else if (testInfo.description.getForeground().equals(WARN_COLOR)) {
			return "warn";
		} else if (testInfo.description.getForeground().equals(PASS_COLOR)) {
			return "pass";
		} else {
			if (testInfo.details.containsKey("passed")) {
				String passed = testInfo.details.get("passed");
				return passed.equalsIgnoreCase("true") ? "pass" : "fail";
			} else {
				return "unknown";
			}
		}
	}
	
	public boolean isChecked(String testName) {
		TestInfo testInfo = allTests.get(testName);
		if (testInfo == null) {
			return false;
		} else {
			return testInfo.checked.isSelected();
		}
	}
	
	public boolean isEmpty() {
		return allCategories.isEmpty();
	}
	
	public boolean isStyleCheck() {
		return this == styleCheckInstance || frame.getTitle().equals(STYLE_CHECK_TITLE);
	}
	
	public void mouseClicked(MouseEvent event) {
		JLabel label = (JLabel) event.getSource();
		TestInfo testInfo = allTests.get(label);
		String testName = testInfo.name;
		if (testName != null) {
			showTestDetails(testName);
		}
	}
	
	public void mouseEntered(MouseEvent event) {
		// empty
	}
	
	public void mouseExited(MouseEvent event) {
		// empty
	}
	
	public void mousePressed(MouseEvent event) {
		// empty
	}
	
	public void mouseReleased(MouseEvent event) {
		// empty
	}
	
	public void setChecked(String testName, boolean checked) {
		TestInfo testInfo = allTests.get(testName);
		if (testInfo != null) {
			testInfo.checked.setSelected(checked);
		}
	}
	
	public void setDescription(String text) {
		text = text.replaceAll("[ \t\r\n\f]+", " ");
		text = text.replaceAll("Note:", "<b>Note:</b>");
		text = text.replaceAll("Warning:", "<b>Warning:</b>");
		text = text.replaceAll("Error:", "<b>Error:</b>");
		descriptionLabel.setText("<html><body style='width: " + DIALOG_WIDTH + "; max-width: " + DIALOG_WIDTH + "'><center>" + text + "</center></body></html>");
		descriptionLabel.validate();
		checkVisibility();
	}
	
	public void setTestCounts(int passCount, int testCount) {
		this.passCount = passCount;
		this.testCount = testCount;
		updateSouthText();
	}
	
	public void setTestDetails(String testName, Map<String, String> details) {
		TestInfo testInfo = allTests.get(testName);
		if (testInfo == null) {
			return;
		}

		// BUGFIX: don't replace test details if a test already failed here
		String existingResult = getTestResult(testName).intern();
		if (existingResult == "fail" || existingResult == "warn") {
			if (!testInfo.details.isEmpty()) {
				return;
			}
		}
		
		testInfo.details = details;
	}
	
	public void setTestingCompleted(boolean completed) {
		testingIsInProgress = !completed;
		updateSouthText();
	}
	
	public boolean setTestResult(String testName, String result) {
		result = result.toLowerCase().intern();
		if (result == "error") {
			result = "fail";  // synonyms
		}
		
		TestInfo testInfo = allTests.get(testName);
		if (testInfo == null) {
			return false;
		}
		
		// BUGFIX: if test already failed previously, don't set back to passed
		String existingResult = getTestResult(testName).intern();
		if ((existingResult == "fail" || existingResult == "warn") && result != "fail") {
			return true;
		}
		
		testInfo.result.setIcon(new ImageIcon(result + ".gif"));   // pass, fail, running, warn
		if (result == "pass") {
			passCount++;
			testInfo.description.setForeground(PASS_COLOR);
		} else if (result == "fail") {
			testInfo.description.setForeground(FAIL_COLOR);
		} else if (result == "warn") {
			testInfo.description.setForeground(WARN_COLOR);
		} else if (result == "warn") {
			testInfo.description.setForeground(NORMAL_COLOR);
		}
		updateSouthText();
		return true;
	}
	
	public boolean setTestRuntime(String testName, int runtimeMS) {
		TestInfo testInfo = allTests.get(testName);
		if (testInfo == null) {
			return false;
		} else {
			String text = " (" + runtimeMS + "ms)";
			testInfo.result.setText(text);
			return true;
		}
	}
	
	public void setVisible(boolean visible) {
		frame.setVisible(visible);
	}
	
	private void checkVisibility() {
		contentPaneBox.revalidate();
		scroll.revalidate();
		// frame.revalidate();
		frame.validate();
		frame.pack();
		frame.setSize(frame.getWidth() + 32, frame.getHeight() + 10);  // a bit of buffer for scrollbar
//		if (frame.isVisible() != !isEmpty()) {
//			frame.setVisible(!isEmpty());
//		}
		
		// scroll to bottom as new tests appear
		// scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
	}
	
	private void selectAll(Container category, boolean selected) {
		for (Component comp : category.getComponents()) {
			if (comp instanceof JCheckBox) {
				JCheckBox checkBox = (JCheckBox) comp;
				checkBox.setSelected(selected);
			} else if (comp instanceof Container) {
				Container container = (Container) comp;
				selectAll(container, selected);
			}
		}
	}
	
	/*
		enum UnitTestType {
		    ASSERT_EQUALS = 0,
		    ASSERT_NOT_EQUALS,
		    ASSERT_NEAR,
		    ASSERT_DIFF,
		    ASSERT_TRUE,
		    ASSERT_FALSE,
		    EXCEPTION,
		    NOT_EXCEPTION,
		    PASS,
		    FAIL,
		};
	 */
	private void showTestDetails(String testName) {
		// {testType=TEST_ASSERT_EQUALS,
		//  message="......",
		//  expected=foobarbaz,
		//  actual=foobarbaz,
		//  valueType=string,
		//  passed=true}
		TestInfo testInfo = allTests.get(testName);
		if (testInfo == null) {
			return;
		}
		Map<String, String> deets = testInfo.details;
		if (deets.isEmpty()) {
			return;
		}
		
		boolean passed = deets.containsKey("passed") && deets.get("passed").equalsIgnoreCase("true");
		String type = deets.containsKey("testType") ? deets.get("testType").toUpperCase().intern() : "";
		String message = deets.get("message");
		if (type == "ASSERT_EQUALS") {
			message += " (must be equal)";
		} else if (type == "ASSERT_NOT_EQUALS") {
			message += " (must be non-equal)";
		} else if (type == "ASSERT_NEAR") {
			message += " (must be nearly equal)";
		} else if (type == "ASSERT_DIFF") {
			message += " (program output must match)";
		} else if (type == "ASSERT_TRUE") {
			message += " (must be true)";
		} else if (type == "ASSERT_FALSE") {
			message += " (must be false)";
		} else if (type == "EXCEPTION") {
			// message += " (threw exception)";
		} else if (type == "NOT_EXCEPTION") {
			message += " (didn't throw expected exception)";
		} else if (type == "PASS") {
			message += " (passed)";
		} else if (type == "FAIL") {
			message += " (failed)";
		} else if (type == "STYLE_CHECK") {
			// message += " (style checker warning)";
		}
		
		// simple expected/actual tests (show both as bullets)
		String expected = deets.get("expected");
		String student  = deets.get("student");
		String valueType = deets.containsKey("valueType") ? deets.get("valueType").toLowerCase().intern() : "";
		if (valueType == "string") {
			expected = "\"" + expected + "\"";
			student  = "\"" + student  + "\"";
		} else if (valueType == "char") {
			expected = "'" + expected + "'";
			student  = "'" + student  + "'";
		}
		
		boolean shouldShowJOptionPane = true;
		if (type == "ASSERT_EQUALS" || type == "ASSERT_NOT_EQUALS" || type == "ASSERT_NEAR" || type == "STYLE_CHECK") {
			String htmlMessage = "";
			htmlMessage += "<html><body style='max-width: " + DIALOG_WIDTH + "px;'>";
			htmlMessage += "<p>" + message + "</p>";
			htmlMessage += "<ul>";
			htmlMessage += "<li><font style='font-family: monospaced' color='" + DiffGui.EXPECTED_COLOR + "'>expected:</font>" + expected + "</li>";
			htmlMessage += "<li><font style='font-family: monospaced' color='" + DiffGui.STUDENT_COLOR  + "'>student :</font>" + student  + "</li>";
			htmlMessage += "</ul>";
			htmlMessage += "</body></html>";
			htmlMessage = htmlMessage.replace("\n", "\\n");
			htmlMessage = htmlMessage.replace("\r", "\\r");
			htmlMessage = htmlMessage.replace("\t", "\\t");
			message = htmlMessage;
		} else if (type == "ASSERT_DIFF") {
			shouldShowJOptionPane = false;
			new DiffGui("expected output", expected, "student output", student).show();
		}
		
		if (shouldShowJOptionPane) {
			JOptionPane.showMessageDialog(
					/* parent */ frame,
					message,
					/* title */ testName,
					/* type */ passed ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
		}
		
		setChanged();
		notifyObservers(testName);
	}
	
	private void updateSouthText() {
		String text = "passed " + passCount + " / " + testCount + " tests";
		if (testingIsInProgress) {
			text += " (running ...)";
			if (southLabel.getIcon() == null) {
				southLabel.setIcon(new ImageIcon("progress.gif"));
			}
		} else {
			text += " (complete)";
			southLabel.setIcon(null);
		}
		southLabel.setText(text);
	}
	
	private class AutograderUnitTestGUIWindowAdapter extends WindowAdapter {
		public void windowClosing(WindowEvent event) {
			if (testingIsInProgress) {
				// probably a hung student test case; kill back-end
				javaBackEnd.shutdownBackEnd(/* sendEvent */ true);
			}
		}
	}
}
