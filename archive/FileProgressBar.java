package xerus.monstercat.downloader;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import xerus.util.tools.Tools;

import java.awt.Dimension;

public class FileProgressBar extends JPanel {
	
	final JLabel label;
	final JProgressBar progressBar;
	final JLabel progressLabel;
	
	public FileProgressBar(String text) {
		progressBar = new JProgressBar(0, 1000);
		progressLabel = new JLabel("0 / ????");
		
		Dimension size = new Dimension(100, 15);
		progressBar.setMinimumSize(size);
		progressBar.setPreferredSize(size);
		progressLabel.setMinimumSize(size);
		progressLabel.setPreferredSize(size);
		progressLabel.setMaximumSize(size);
		progressLabel.setAlignmentX(0.5f);
		
		add(label = new JLabel(text));
		add(progressBar);
		add(progressLabel);
	}
	
	public FileProgressBar(String text, int max) {
		this(text);
		setMax(max);
	}
	
	public void setText(String text) {
		label.setText(text);
	}
	
	private String labelSuffix;
	private long val;
	private long divisor;
	public FileProgressBar setMax(long max) {
		divisor = max / 1000;
		labelSuffix = " / " + Tools.byteCountString(max);
		updateLabel();
		return this;
	}
	
	public void updateProgress(long current) {
		val = current;
		progressBar.setValue((int) (current / divisor));
		updateLabel();
	}
	
	private void updateLabel() {
		progressLabel.setText(Tools.byteCountString(val) + labelSuffix);
	}
	
}
