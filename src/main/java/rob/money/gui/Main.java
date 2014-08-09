package rob.money.gui;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.prefs.Preferences;

import rob.money.database.AccountModel;
import rob.money.database.Accounts;
import rob.money.database.Banks;
import rob.money.database.BankAccountModel;
import rob.money.database.Categories;
import rob.money.database.Controller;
import rob.money.database.DatabaseException;
import rob.money.database.DatabaseChangeEvent;
import rob.money.database.DatabaseChangeListener;
import rob.money.database.DatabaseTable;
import rob.money.database.FundAccountModel;
import rob.money.database.Funds;
import rob.money.database.Transactees;
import rob.money.database.TransactionSchedule;
import rob.money.gui.editors.BankDepositEditor;
import rob.money.gui.editors.BankTransferEditor;
import rob.money.gui.editors.BankWithdrawalEditor;
import rob.money.gui.editors.DefundEditor;
import rob.money.gui.editors.EditPanel;
import rob.money.gui.editors.EnfundEditor;
import rob.money.gui.editors.FundTransferEditor;
import rob.money.gui.editors.ScheduledBankCreditEditor;
import rob.money.gui.editors.ScheduledBankDebitEditor;
import rob.money.gui.editors.ScheduledBankTransferEditor;
import rob.money.gui.editors.TransactionEditor;

public class Main implements DatabaseChangeListener, ActionListener {

	private Connection connection;

	private JFrame main;

	private NumberFormat amountFormat = NumberFormat.getNumberInstance();

	private final Color MAIN_COLOR = new Color(239, 247, 253);

	private final Color TEST_COLOR = new Color(229, 253, 237);

	private Color backgroundColor;

	private Hashtable<String, AccountModel> accountTable = new Hashtable<String, AccountModel>();

	private Hashtable<String, AccountModel> fundTable = new Hashtable<String, AccountModel>();

	private Banks banks;

	private Accounts accounts;

	private Funds funds;

	private Transactees transactees;

	private Categories categories;

	private Controller controller;

	private TransactionSchedule transactionSchedule;

	public Main(Connection connection) throws SQLException, DatabaseException {
		this(connection, false);
	}

	public Main(Connection connection, boolean test) throws DatabaseException {
		if (test) {
			backgroundColor = TEST_COLOR;
		} else {
			backgroundColor = MAIN_COLOR;
		}

		controller = Controller.getInstance(connection);
		controller.addDatabaseChangeListener(this);
		this.connection = connection;
		banks = controller.getBanks();
		accounts = controller.getAccounts();
		funds = controller.getFunds();
		transactees = controller.getTransactees();
		categories = controller.getCategories();
		setupAccounts(connection);
		transactionSchedule = new TransactionSchedule(controller);
		transactionSchedule.scan();
		amountFormat.setGroupingUsed(true);
		Currency currency = Currency.getInstance("AUD");
		amountFormat.setCurrency(currency);

		main = makePanel(accountTable, fundTable);
		main.setSize(1000, 700);

		if ((accounts.get().length == 0) || (banks.get().length == 0)) {
			ConfigWizard wizard = new ConfigWizard(main, connection);
			wizard.addActionListener(this);
			JDialog dialog = wizard.getDialog();
			dialog.setVisible(true);
		} else {
			main.setVisible(true);
		}
	}

	private void makeMenus(final JFrame mainFrame, final Accounts accounts,
			final Funds funds, final Transactees transactees,
			final Categories categories) throws DatabaseException {

		Color labelColor = new Color(165, 199, 238);

		JMenuBar menuBar = new JMenuBar();

		JMenu fileMenu = new JMenu("File");
		fileMenu.setBackground(labelColor);
		JMenuItem quitItem = new JMenuItem("Quit");
		quitItem.setBackground(labelColor);
		QuitAction quitAction = new QuitAction();
		quitItem.addActionListener(quitAction);
		fileMenu.add(quitItem);
		menuBar.add(fileMenu);

		final JDialog bankDialog = new BankDialog(mainFrame, banks);
		final JDialog transacteeDialog = new TransacteeDialog(mainFrame,
				transactees);
		final JDialog categoryDialog = new CategoryDialog(mainFrame, categories);
		final JDialog accountsDialog = new AccountsDialog(mainFrame, banks,
				accounts);
		final JDialog fundsDialog = new FundsDialog(mainFrame, funds);

		JMenu viewMenu = new JMenu("View");
		viewMenu.setBackground(labelColor);

		JMenuItem banksMenu = new JMenuItem("Banks");
		banksMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				bankDialog.setVisible(true);
			}
		});
		banksMenu.setBackground(labelColor);
		JMenuItem accountsMenu = new JMenuItem("Accounts");
		accountsMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				accountsDialog.setVisible(true);
			}
		});
		accountsMenu.setBackground(labelColor);
		JMenuItem fundsMenu = new JMenuItem("Funds");
		fundsMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				fundsDialog.setVisible(true);
			}
		});
		fundsMenu.setBackground(labelColor);
		JMenuItem transacteeMenu = new JMenuItem("Transactees");
		transacteeMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				transacteeDialog.setVisible(true);
			}
		});
		transacteeMenu.setBackground(labelColor);
		JMenuItem categoryMenu = new JMenuItem("Categories");
		categoryMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				categoryDialog.setVisible(true);
			}
		});
		categoryMenu.setBackground(labelColor);

		viewMenu.add(banksMenu);
		viewMenu.add(accountsMenu);
		viewMenu.add(fundsMenu);
		viewMenu.add(transacteeMenu);
		viewMenu.add(categoryMenu);
		menuBar.add(viewMenu);

		menuBar.setBackground(labelColor);
		mainFrame.setJMenuBar(menuBar);
	}

	protected class BankDialog extends JDialog {
		private BankPanel bankPanel;

		public BankDialog(JFrame parent, Banks banks) throws DatabaseException {
			super(parent, "Transactees");

			JPanel panel = new JPanel();
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();
			panel.setLayout(layout);

			bankPanel = new BankPanel(banks);

			JPanel buttonPanel = new JPanel();

			JButton closeButton = new JButton("Close");
			closeButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					BankDialog.this.setVisible(false);
				}
			});
			buttonPanel.add(closeButton);

			c.gridx = 0;
			c.gridy = 0;
			layout.setConstraints(bankPanel, c);
			panel.add(bankPanel);

			c.gridx = 0;
			c.gridy = 1;
			layout.setConstraints(buttonPanel, c);
			panel.add(buttonPanel);

			this.getContentPane().add(panel);
			pack();
		}
	}

	private class AccountsDialog extends JDialog {
		private Accounts accounts;

		private AccountPanel accountPanel;

		public AccountsDialog(JFrame parent, Banks banks, Accounts accounts)
				throws DatabaseException {
			super(parent, "Accounts");
			this.accounts = accounts;

			JPanel panel = new JPanel();
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();
			panel.setLayout(layout);

			accountPanel = new AccountPanel(banks, accounts);

			JPanel buttonPanel = new JPanel();

			JButton closeButton = new JButton("Close");
			closeButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					AccountsDialog.this.setVisible(false);
				}
			});
			buttonPanel.add(closeButton);

			c.gridx = 0;
			c.gridy = 0;
			layout.setConstraints(accountPanel, c);
			panel.add(accountPanel);

			c.gridx = 0;
			c.gridy = 1;
			c.weighty = 1.0;
			c.anchor = GridBagConstraints.PAGE_END;
			layout.setConstraints(buttonPanel, c);
			panel.add(buttonPanel);

			this.getContentPane().add(panel);
			pack();
		}
	}

	private class FundsDialog extends JDialog {
		private Funds funds;

		private FundPanel fundsPanel;

		public FundsDialog(JFrame parent, Funds funds) throws DatabaseException {
			super(parent, "Funds");
			this.funds = funds;

			JPanel panel = new JPanel();
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();
			panel.setLayout(layout);

			JLabel fundsLabel = new JLabel("Funds");

			fundsPanel = new FundPanel(accounts, funds);

			JPanel buttonPanel = new JPanel();

			JButton closeButton = new JButton("Close");
			closeButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					FundsDialog.this.setVisible(false);
				}
			});
			buttonPanel.add(closeButton);

			Insets insets = new Insets(10, 5, 5, 5);
			c.gridx = 0;
			c.gridy = 0;
			c.gridwidth = 2;
			c.insets = insets;
			layout.setConstraints(fundsLabel, c);
			panel.add(fundsLabel);

			c.gridx = 0;
			c.gridy = 1;
			layout.setConstraints(fundsPanel, c);
			panel.add(fundsPanel);

			c.gridx = 0;
			c.gridy = 2;
			c.weighty = 1.0;
			c.anchor = GridBagConstraints.PAGE_END;
			layout.setConstraints(buttonPanel, c);
			panel.add(buttonPanel);

			this.getContentPane().add(panel);
			pack();
		}
	}

	protected class TransacteeDialog extends JDialog {
		private TransacteePanel transPanel;

		public TransacteeDialog(JFrame parent, Transactees transactees)
				throws DatabaseException {
			super(parent, "Transactees");

			JPanel panel = new JPanel();
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();
			panel.setLayout(layout);

			transPanel = new TransacteePanel(transactees);

			JPanel buttonPanel = new JPanel();

			JButton closeButton = new JButton("Close");
			closeButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					TransacteeDialog.this.setVisible(false);
				}
			});
			buttonPanel.add(closeButton);

			c.gridx = 0;
			c.gridy = 0;
			layout.setConstraints(transPanel, c);
			panel.add(transPanel);

			c.gridx = 0;
			c.gridy = 1;
			layout.setConstraints(buttonPanel, c);
			panel.add(buttonPanel);

			this.getContentPane().add(panel);
			pack();
		}
	}

	protected class CategoryDialog extends JDialog {
		private CategoryPanel categoryPanel;

		public CategoryDialog(JFrame parent, Categories categories)
				throws DatabaseException {
			super(parent, "Categories");

			JPanel panel = new JPanel();
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();
			panel.setLayout(layout);

			categoryPanel = new CategoryPanel(categories);

			JPanel buttonPanel = new JPanel();

			JButton closeButton = new JButton("Close");
			closeButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					CategoryDialog.this.setVisible(false);
				}
			});
			buttonPanel.add(closeButton);

			c.gridx = 0;
			c.gridy = 0;
			layout.setConstraints(categoryPanel, c);
			panel.add(categoryPanel);

			c.gridx = 0;
			c.gridy = 1;
			layout.setConstraints(buttonPanel, c);
			panel.add(buttonPanel);

			this.getContentPane().add(panel);
			pack();
		}
	}

	private void setupAccounts(Connection connection) throws DatabaseException {
		Accounts accounts = controller.getAccounts();
		Accounts.BankAccount[] accArr = accounts.get();
		for (Accounts.BankAccount nextAccount : accArr) {
			BankAccountModel bankTransactions = new BankAccountModel(
					connection, nextAccount);
			String name = bankTransactions.getName();
			accountTable.put(name, bankTransactions);
			controller.addDatabaseChangeListener(bankTransactions);
		}
		Funds funds = controller.getFunds();
		Funds.Fund[] fundArr = funds.get();
		for (Funds.Fund nextFund : fundArr) {
			FundAccountModel fundTransactions = new FundAccountModel(
					connection, nextFund);
			String name = fundTransactions.getName();
			fundTable.put(name, fundTransactions);
			controller.addDatabaseChangeListener(fundTransactions);
		}
	}

	private JFrame makePanel(Hashtable<String, AccountModel> accountTable,
			Hashtable<String, AccountModel> fundTable) throws DatabaseException {
		Color labelColor = new Color(165, 199, 238);

		JFrame mainFrame = new JFrame("Money");
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		panel.setBackground(backgroundColor);

		makeMenus(mainFrame, accounts, funds, transactees, categories);

		JTabbedPane tabs = new JTabbedPane();
		AccountsPanel accountsPanel = new AccountsPanel(accountTable);
		FundsPanel fundsPanel = new FundsPanel(fundTable);
		SchedulePanel schedulePanel = new SchedulePanel(transactionSchedule);
		controller.addDatabaseChangeListener(accountsPanel);
		controller.addDatabaseChangeListener(fundsPanel);
		tabs.addTab("Accounts", accountsPanel);
		tabs.addTab("Funds", fundsPanel);
		tabs.addTab("Schedule", schedulePanel);
		if (fundTable.size() == 0) {
			tabs.setEnabledAt(1, false);
		} else {
			tabs.setEnabledAt(1, true);
		}

		mainFrame.getContentPane().add(tabs);

		return mainFrame;
	}

	private class AccountsPanel extends JPanel implements
			DatabaseChangeListener {
		private JComboBox<String> accountCombo;

		public BalanceLabel finalBalance;

		public TransactionTable table;

		public AccountsPanel(Hashtable<String, AccountModel> accountTable) {
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();

			Color labelColor = new Color(165, 199, 238);
			finalBalance = new BalanceLabel(amountFormat);
			finalBalance.setBackground(labelColor);

			setBackground(backgroundColor);
			setLayout(layout);

			Enumeration<String> accountEnum = accountTable.keys();

			table = new TransactionTable();
			if (accountTable.size() > 0) {
				AccountModel model = accountTable.get(accountEnum.nextElement());
				table.setModel(model);
			}

			TransactionEditor transactionEditor = new TransactionEditor(
					accountTable);
			try {
				EditPanel editor = new BankWithdrawalEditor();
				System.err.println("Create editor: " + editor.getType());
				transactionEditor.addPanel(editor);
				editor = new BankDepositEditor();
				transactionEditor.addPanel(editor);
				editor = new BankTransferEditor();
				transactionEditor.addPanel(editor);
			} catch (DatabaseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			table.setEditor(transactionEditor);

			Insets insets = new Insets(10, 5, 5, 5);
			c.gridx = 0;
			c.gridy = 0;
			c.insets = insets;
			accountCombo = new JComboBox<String>();
			fillAccountCombo(accountCombo, accounts);
			AccountAction accountAction = new AccountAction(this);
			accountCombo.addActionListener(accountAction);
			accountCombo.addActionListener(transactionEditor);
			if (accounts.get().length > 0) {
				accountCombo.setSelectedIndex(0);
			}
			layout.setConstraints(accountCombo, c);
			add(accountCombo);

			c.gridx = 0;
			c.gridy = 1;
			c.gridwidth = 2;
			c.weightx = 1;
			c.weighty = 1;
			c.fill = GridBagConstraints.BOTH;

			JScrollPane transScroll = new JScrollPane(table);
			layout.setConstraints(transScroll, c);
			add(transScroll);

			c.gridx = 0;
			c.gridy = 2;
			c.gridwidth = 2;
			c.weightx = 0;
			c.weighty = 0;
			c.anchor = GridBagConstraints.EAST;
			c.insets = new Insets(0, 0, 10, 0);
			JPanel x = new JPanel();
			x.setBackground(labelColor);
			x.add(finalBalance);
			layout.setConstraints(x, c);
			add(x);

			c.gridx = 0;
			c.gridy = 3;
			c.gridwidth = 2;
			c.anchor = GridBagConstraints.CENTER;
			layout.setConstraints(transactionEditor, c);
			add(transactionEditor);
		}

		private void fillAccountCombo(JComboBox<String> combo, Accounts accounts) {
			combo.removeAllItems();
			Accounts.BankAccount[] list = accounts.get();
			for (Accounts.BankAccount account : list) {
				combo.addItem(account.getName());
			}
		}

		@Override
		public void stateChanged(DatabaseChangeEvent e) {
			int type = e.getType();
			if (type == DatabaseChangeEvent.ACCOUNT_ADDED) {
				Accounts.BankAccount newAccount = (Accounts.BankAccount) e
						.getSource();
				accountCombo.addItem(newAccount.getName());
			} else if (type == DatabaseChangeEvent.ACCOUNT_REMOVED) {
				Accounts.BankAccount removedAccount = (Accounts.BankAccount) e
						.getSource();
				accountCombo.removeItem(removedAccount.getName());
			}
		}

	}

	private class FundsPanel extends JPanel implements DatabaseChangeListener {

		private JComboBox<String> fundCombo;

		public BalanceLabel finalBalance;

		public TransactionTable table;

		public FundsPanel(Hashtable<String, AccountModel> fundTable) {

			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();

			Color labelColor = new Color(165, 199, 238);
			finalBalance = new BalanceLabel(amountFormat);
			finalBalance.setBackground(labelColor);

			setBackground(backgroundColor);
			setLayout(layout);

			Enumeration<String> fundEnum = fundTable.keys();
			table = new TransactionTable();

			TransactionEditor transactionEditor = new TransactionEditor(
					fundTable);
			try {
				EditPanel editor = new EnfundEditor();
				transactionEditor.addPanel(editor);
				editor = new DefundEditor();
				transactionEditor.addPanel(editor);
				editor = new FundTransferEditor();
				transactionEditor.addPanel(editor);
				table.setEditor(transactionEditor);
			} catch (DatabaseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Insets insets = new Insets(10, 5, 5, 5);
			c.gridx = 0;
			c.gridy = 0;
			c.insets = insets;
			java.util.Vector<String> accountVect = new java.util.Vector<String>();
			while (fundEnum.hasMoreElements()) {
				accountVect.add(fundEnum.nextElement());
			}
			fundCombo = new JComboBox<String>(accountVect);
			FundAction fundAction = new FundAction(this);
			fundCombo.addActionListener(fundAction);
			fundCombo.addActionListener(transactionEditor);
			if (accountVect.size() > 0) {
				fundCombo.setSelectedIndex(0);
			}
			layout.setConstraints(fundCombo, c);
			add(fundCombo);

			c.gridx = 0;
			c.gridy = 1;
			c.weightx = 1;
			c.weighty = 1;
			c.gridwidth = 2;
			c.fill = GridBagConstraints.BOTH;

			JScrollPane transScroll = new JScrollPane(table,
					JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			layout.setConstraints(transScroll, c);
			add(transScroll);

			c.gridx = 0;
			c.gridy = 2;
			c.gridwidth = 2;
			c.weightx = 0;
			c.weighty = 0;
			c.anchor = GridBagConstraints.EAST;
			c.insets = new Insets(0, 0, 10, 0);
			JPanel x = new JPanel();
			x.setBackground(labelColor);
			x.add(finalBalance);
			layout.setConstraints(x, c);
			add(x);

			c.gridx = 0;
			c.gridy = 3;
			c.gridwidth = 2;
			c.anchor = GridBagConstraints.CENTER;
			layout.setConstraints(transactionEditor, c);
			add(transactionEditor);
		}

		@Override
		public void stateChanged(DatabaseChangeEvent e) {
			int type = e.getType();
			if (type == DatabaseChangeEvent.FUND_ADDED) {
				Funds.Fund newFund = (Funds.Fund) e.getSource();
				fundCombo.addItem(newFund.getName());
			} else if (type == DatabaseChangeEvent.FUND_REMOVED) {
				Funds.Fund removedFund = (Funds.Fund) e.getSource();
				fundCombo.removeItem(removedFund.getName());
			}
		}

	}

	public class SchedulePanel extends JPanel implements ActionListener,
			DatabaseChangeListener {

		public SchedulePanel(TransactionSchedule transactionSchedule)
				throws DatabaseException {

			// Get control of the database
			Controller controller = Controller.getInstance();
			controller.addDatabaseChangeListener(this);

			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();

			Color labelColor = new Color(165, 199, 238);

			setBackground(backgroundColor);
			setLayout(layout);

			ScheduleTable scheduleTable = new ScheduleTable(transactionSchedule);
			TransactionEditor transactionEditor = new TransactionEditor(
					fundTable);
			EditPanel editor = new ScheduledBankCreditEditor(
					transactionSchedule);
			transactionEditor.addPanel(editor);
			editor = new ScheduledBankDebitEditor(transactionSchedule);
			transactionEditor.addPanel(editor);
			editor = new ScheduledBankTransferEditor(transactionSchedule);
			transactionEditor.addPanel(editor);
			scheduleTable.setEditor(transactionEditor);

			Insets insets = new Insets(10, 5, 5, 5);
			c.gridx = 0;
			c.gridy = 0;
			c.insets = insets;
			JLabel label = new JLabel("Scheduled Transactions");
			layout.setConstraints(label, c);
			add(label);

			c.gridx = 0;
			c.gridy = 1;
			c.weightx = 1;
			c.weighty = 1;
			c.fill = GridBagConstraints.BOTH;
			JScrollPane schedScroll = new JScrollPane(scheduleTable,
					JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			layout.setConstraints(schedScroll, c);
			add(schedScroll);

			c.gridx = 0;
			c.gridy = 2;
			c.gridwidth = 2;
			c.anchor = GridBagConstraints.CENTER;
			layout.setConstraints(transactionEditor, c);
			add(transactionEditor);
		}

		@Override
		public void stateChanged(DatabaseChangeEvent e) {
			int type = e.getType();
			if (type == DatabaseChangeEvent.SCHEDULED_TRANSACTION_ADDED) {

			}
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub

		}

	}

	private class AccountAction extends AbstractAction {
		private AccountsPanel accountPanel;

		public AccountAction(AccountsPanel accountPanel) {
			this.accountPanel = accountPanel;
		}

		public void actionPerformed(ActionEvent event) {
			JComboBox<String> accountCombo = (JComboBox<String>) event
					.getSource();
			String accountName = (String) accountCombo.getSelectedItem();
			AccountModel model = accountTable.get(accountName);
			accountPanel.table.setModel(model);
			accountPanel.finalBalance.setTransactions(model);
		}
	}

	private class FundAction extends AbstractAction {
		private FundsPanel fundPanel;

		public FundAction(FundsPanel fundPanel) {
			this.fundPanel = fundPanel;
		}

		public void actionPerformed(ActionEvent event) {
			JComboBox<String> fundCombo = (JComboBox<String>) event.getSource();
			String fundName = (String) fundCombo.getSelectedItem();
			if (fundName != null) {
				AccountModel model = fundTable.get(fundName);
				fundPanel.table.setModel(model);
				fundPanel.finalBalance.setTransactions(model);
			}
		}
	}

	private class BalanceLabel extends JLabel implements
			javax.swing.event.TableModelListener {
		NumberFormat amountFormat;

		private AccountModel transactions;

		public BalanceLabel(AccountModel transactions, NumberFormat amountFormat) {
			super();
			this.amountFormat = amountFormat;
			setTransactions(transactions);
		}

		public BalanceLabel(NumberFormat amountFormat) {
			super();
			this.amountFormat = amountFormat;
		}

		private void makeText(AccountModel transactions) {
			String text = "Final Balance: "
					+ amountFormat.format(transactions.getFinalBalance());
			setText(text);
		}

		public void setTransactions(AccountModel newTransactions) {
			// Remove listener from old transactions model (if there is one)
			if (transactions != null) {
				transactions.removeTableModelListener(this);
			}
			newTransactions.addTableModelListener(this);
			transactions = newTransactions;
			makeText(transactions);
		}

		@Override
		public void tableChanged(TableModelEvent arg0) {
			AccountModel model = (AccountModel) arg0.getSource();
			makeText(model);
		}
	}

	private class QuitAction extends AbstractAction {
		public void actionPerformed(ActionEvent event) {
			closeConnection(connection);
			System.exit(0);
		}

	}

	private static Connection getConnection(String hostname, String database) {
		Connection conn = null;

		try {
			String userName = "rob";
			String password = "gwatamap1";
			String url = "jdbc:mysql://" + hostname + "/" + database;
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conn = DriverManager.getConnection(url, userName, password);
		} catch (Exception e) {
			System.err.println("Cannot connect to database server");
			e.printStackTrace();
		}

		return conn;
	}

	private static void closeConnection(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) { /* ignore close errors */
			}
		}
	}

	class CellRenderer extends DefaultTableCellRenderer {
		private Color dark = new Color(246, 250, 253);
		private Color light = new Color(255, 255, 255);

		public java.awt.Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {

			super.getTableCellRendererComponent(table, value, isSelected,
					hasFocus, row, column);
			if ((row % 2) == 0) {
				setBackground(dark);
			} else {
				setBackground(light);
			}

			return this;
		}

	}

	/**
	 * Listen to all of the dialogs for changes to the data
	 */
	@Override
	public void stateChanged(DatabaseChangeEvent e) {
		int type = e.getType();
		try {
			if (type == DatabaseChangeEvent.ACCOUNT_ADDED) {
				Accounts.BankAccount newAccount = (Accounts.BankAccount) e
						.getSource();
				BankAccountModel bankAccount = new BankAccountModel(connection,
						newAccount.getID());
				String name = bankAccount.getName();
				accountTable.put(name, bankAccount);
			} else if (type == DatabaseChangeEvent.ACCOUNT_REMOVED) {
				Accounts.BankAccount oldAccount = (Accounts.BankAccount) e
						.getSource();
				String name = oldAccount.getName();
				accountTable.remove(name);
			} else if (type == DatabaseChangeEvent.FUND_ADDED) {
				Funds.Fund newFund = (Funds.Fund) e.getSource();
				FundAccountModel fundAccount = new FundAccountModel(connection,
						newFund.getID());
				String name = newFund.getName();
				fundTable.put(name, fundAccount);
			}
		} catch (SQLException ex) {
			System.err.println("Error " + ex);
		} catch (DatabaseException ex) {
			System.err.println("Error " + ex);
		}
	}

	private class Style {
		private Color backgroundColor;

		public Style(Color backgroundColor) {
			setBackgroundColor(backgroundColor);
		}

		public void setBackgroundColor(Color color) {
			this.backgroundColor = color;
		}

		public Color getBackgroundColor() {
			return backgroundColor;
		}
	}

	public static final void main(String[] args) {
		boolean test = false;

		try {
			Preferences userPreferences = Preferences.userRoot();
			Preferences moneyPrefs = userPreferences.node("money");
			DatabaseTable dbDefs = new DatabaseTable(
					moneyPrefs.node("database"));

			String database = "default";

			String hostname = "robs-desktop";
			if (args.length > 0) {
				if (args[0].equals("test")) {
					test = true;
					hostname = "raspberrypi1";
					database = "testing";
				} else if (args[0].equals("develop")) {
					test = true;
					hostname = "raspberrypi2";
					database = "newconn";
				} else if (args[0].equals("macbook")) {
					test = true;
					hostname = "robs-macbook";
					database = "robs-macbook";
				}
			}

			SplashScreen splash = new SplashScreen(hostname);

			splash.showScreen();
			// Connection connection = getConnection(hostname, "funds");
			Connection connection = null;
			if (database.equals("default")) {
				connection = dbDefs.getConnection(DatabaseTable.OPERATIONAL);
			} else {
				connection = dbDefs.getConnection(database);
			}

			splash.hideScreen();

			if (connection == null) {
				// Prompt user for a database
				DatabaseDefiner definer = new DatabaseDefiner(null, dbDefs);
				String selectedDB = definer.getSelectedDatabase();
				dbDefs.setStatus("Default", selectedDB);
				splash.showScreen();
				connection = dbDefs.getDefaultConnection();
				splash.hideScreen();
			}

			if (connection != null) {
				Main main = new Main(connection, test);
			}
		} catch (DatabaseException e) {
			System.err.println("There's something wrong with me " + e);
			e.printStackTrace();
			System.exit(-1);
		}

	}

	@Override
	/**
	 * Listen to the ConfigWizard
	 * @param e ActionEvent
	 */
	public void actionPerformed(ActionEvent e) {
		int type = e.getID();
		if (type == ConfigWizard.WIZARD_FINISHED) {
			ConfigWizard wizard = (ConfigWizard) e.getSource();
			wizard.hide();
			main.setVisible(true);
		}
	}

}
