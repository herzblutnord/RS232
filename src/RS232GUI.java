// jSerialCom imports
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
// ui design imports
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
// io imports
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class RS232GUI {
	private SerialPort serialPort;
	private BufferedReader input;
	private OutputStream output;
	private JTextPane dataCommunicationPane;
	private SimpleAttributeSet receivedAttributeSet;
	private SimpleAttributeSet sentAttributeSet;
	private JLabel selectedCOMPortLabel;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new RS232GUI().createAndShowGUI());
	}

	private void createAndShowGUI() {

		// Set the FlatLaf look and feel
		try {
			UIManager.setLookAndFeel(new FlatLightLaf());
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Create the main window
		JFrame frame = new JFrame("RS232 GUI");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		Container contentPane = frame.getContentPane();
		contentPane.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		// Create the port panel
		JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		selectedCOMPortLabel = new JLabel("Selected COM Port: N/A");
		portPanel.add(selectedCOMPortLabel);
		JButton refreshButton = new JButton("Refresh");
		portPanel.add(refreshButton);
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		contentPane.add(portPanel, gbc);

		// Create the input panel
		JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		inputPanel.add(new JLabel("Single Command Window:"));
		JTextField singleCommandWindowField = new JTextField(20);
		inputPanel.add(singleCommandWindowField);
		JButton sendButton = new JButton("Send");
		inputPanel.add(sendButton);
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		contentPane.add(inputPanel, gbc);

		// Create the data communication panel
		dataCommunicationPane = new JTextPane();
		dataCommunicationPane.setEditable(false);
		JScrollPane dataCommunicationScrollPane = new JScrollPane(dataCommunicationPane);
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		contentPane.add(dataCommunicationScrollPane, gbc);

		// Initialize font attributes
		receivedAttributeSet = new SimpleAttributeSet();
		StyleConstants.setForeground(receivedAttributeSet, Color.RED);
		sentAttributeSet = new SimpleAttributeSet();
		StyleConstants.setForeground(sentAttributeSet, Color.GREEN);

		// Initialize the COM port
		detectAndInitCOMPort(9600);

		// Send button listener
		sendButton.addActionListener(e -> {
			String singleCommand = singleCommandWindowField.getText();
			sendData(singleCommand + "\r");
		});

		// Refresh button listener
		refreshButton.addActionListener(e -> detectAndInitCOMPort(9600));

		// Set up the window
		frame.setSize(500, 600);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void initCOMPort(String portName, int baudRate) {
		serialPort = SerialPort.getCommPort(portName);
		serialPort.setComPortParameters(baudRate,
				8,
				1,
				SerialPort.NO_PARITY);
		serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);


		if (serialPort.openPort()) {
			input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
			output = serialPort.getOutputStream();

			serialPort.addDataListener(new SerialReader());
		} else {
			appendToDataCommunication("Error opening the port.\n", receivedAttributeSet);
		}
	}

	private void sendData(String data) {
		if (output == null) {
			appendToDataCommunication("Error: COM port not initialized. Please ensure a device is connected.\n", receivedAttributeSet);
			return;
		}

		try {
			output.write(data.getBytes());
			appendToDataCommunication("Sent data: " + data + "\n", sentAttributeSet);
		} catch (Exception e) {
			appendToDataCommunication("Error sending data: " + e.getMessage() + "\n", receivedAttributeSet);
			e.printStackTrace();
		}
	}


	private void appendToDataCommunication(String text, AttributeSet attributeSet) {
		try {
			dataCommunicationPane.getDocument().insertString(dataCommunicationPane.getDocument().getLength(), text, attributeSet);
			dataCommunicationPane.setCaretPosition(dataCommunicationPane.getDocument().getLength());
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	private void detectAndInitCOMPort(int baudRate) {
		// Close the previously opened port (if any)
		if (serialPort != null && serialPort.isOpen()) {
			serialPort.closePort();
		}

		SerialPort[] portList = SerialPort.getCommPorts();
		SerialPort targetPort = null;

		for (SerialPort port : portList) {
			try {
				initCOMPort(port.getSystemPortName(), baudRate);
				targetPort = port;
				break;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (targetPort == null) {
			appendToDataCommunication("No available COM ports found.\n", receivedAttributeSet);
		} else {
			selectedCOMPortLabel.setText("Selected COM Port: " + targetPort.getSystemPortName());
		}
	}

	private class SerialReader implements SerialPortDataListener {
		private final StringBuilder receivedData = new StringBuilder();
		private int charCount = 0;

		@Override
		public int getListeningEvents() {
			return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
		}

		@Override
		public void serialEvent(SerialPortEvent event) {
			if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
				try {
					int newData;
					while ((newData = input.read()) > -1) {
						char newChar = (char) newData;
						charCount++;

						if (newChar == '\r') {
							String receivedLine = receivedData.toString();
							appendToDataCommunication("Received data: " + receivedLine + "\r\n", receivedAttributeSet);

							receivedData.setLength(0);
							charCount = 0;
						} else {
							if (charCount > 90) {
								appendToDataCommunication("Error: Overflow, no '\\r' received after 90 characters.\n", receivedAttributeSet);
								receivedData.setLength(0);
								charCount = 0;
							} else {
								receivedData.append(newChar);
							}
						}
					}
				} catch (Exception e) {
					appendToDataCommunication("Error reading data: " + e.getMessage() + "\n", receivedAttributeSet);
					e.printStackTrace();
				}
			}
		}
	}
}