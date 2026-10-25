package in.smartvrms.desktop.ui;

import in.smartvrms.contracts.*;
import in.smartvrms.desktop.integration.FleetHttpClient;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.*;
import java.util.*;
import java.util.List;

/** Real records only. All network work runs off the EDT; stale responses cannot repopulate after logout. */
public final class LiveFleetView extends JPanel {
    private final FleetHttpClient api;
    private final boolean administrator;
    private final JButton refresh=Design.button("Refresh fleet",false),add=Design.button("Register vehicle",true),category=Design.button("Add category",false);
    private final JLabel feedback=Design.label("Loading company fleet…",12,Design.MUTED);
    private final DefaultTableModel rows=new DefaultTableModel(new String[]{"Registration","Vehicle","Category","Seats","Daily rate (INR)","Status"},0){
        @Override public boolean isCellEditable(int row,int column){return false;}
    };
    private List<VehicleCategoryRecord> categories=List.of();
    private SwingWorker<?,?> pending;
    private boolean active=true;
    public LiveFleetView(FleetHttpClient api,StaffSession session){
        super(new BorderLayout(12,12));this.api=api;this.administrator=session.isAdministrator();
        setOpaque(false);
        JPanel header=Design.card("Company fleet","Records loaded from your authenticated company account.");
        header.add(Design.row(refresh,add,category));header.add(Box.createVerticalStrut(10));header.add(feedback);add(header,BorderLayout.NORTH);
        JTable table=new JTable(rows);table.setRowHeight(32);table.setAutoCreateRowSorter(true);table.setFillsViewportHeight(true);
        var plainText=new javax.swing.table.DefaultTableCellRenderer();plainText.putClientProperty("html.disable",Boolean.TRUE);table.setDefaultRenderer(Object.class,plainText);
        table.getAccessibleContext().setAccessibleName("Live company vehicle records");
        table.getTableHeader().setFont(new Font(Font.DIALOG,Font.BOLD,12));table.getTableHeader().setPreferredSize(new Dimension(0,34));
        JScrollPane scroller=new JScrollPane(table);scroller.setColumnHeaderView(table.getTableHeader());add(scroller);
        refresh.addActionListener(event->reload());category.addActionListener(event->categoryDialog());add.addActionListener(event->vehicleDialog());
        reload();
    }
    public void close(){active=false;if(pending!=null)pending.cancel(true);rows.setRowCount(0);categories=List.of();}
    private void busy(boolean value){refresh.setEnabled(!value);add.setEnabled(!value&&administrator&&!categories.isEmpty());category.setEnabled(!value&&administrator);}
    private record FleetData(List<VehicleCategoryRecord> categories,List<VehicleRecord> vehicles){}
    public void reload(){
        if(!active)return;busy(true);feedback.setText("Loading company records…");
        pending=new SwingWorker<FleetData,Void>(){
            protected FleetData doInBackground(){return new FleetData(api.categories(),api.vehicles());}
            protected void done(){if(!active)return;try{
                var result=get();categories=result.categories();rows.setRowCount(0);
                for(var item:result.vehicles())rows.addRow(new Object[]{item.registrationPlate(),item.manufacturer()+" "+item.model(),item.categoryName(),item.seatingCapacity(),BigDecimal.valueOf(item.dailyRatePaise(),2).toPlainString(),item.operationalStatus()});
                feedback.setText(result.vehicles().isEmpty()?"No vehicles registered yet. Administrators can add a category and register the first vehicle.":result.vehicles().size()+" vehicle records loaded.");
            }catch(Exception ex){rows.setRowCount(0);categories=List.of();failure(ex);}finally{busy(false);}}
        };pending.execute();
    }
    private void categoryDialog(){
        JTextField code=new JTextField(),name=new JTextField();JPanel form=new JPanel(new GridLayout(0,2,10,10));
        form.add(new JLabel("Code (e.g. SUV)"));form.add(code);form.add(new JLabel("Display name"));form.add(name);
        if(JOptionPane.showConfirmDialog(this,form,"Add vehicle category",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String key=code.getText().strip().toUpperCase(Locale.ROOT),label=name.getText().strip();
        if(!key.matches("[A-Z][A-Z0-9_]{1,19}")||label.isBlank()||label.length()>50){feedback.setText("Enter a 2–20 character category code and a name up to 50 characters.");return;}
        save(()->api.createCategory(key,label),"Category saved.");
    }
    private void vehicleDialog(){
        JComboBox<VehicleCategoryRecord> categoryList=new JComboBox<>(categories.toArray(VehicleCategoryRecord[]::new));
        var plainCategory=new DefaultListCellRenderer();plainCategory.putClientProperty("html.disable",Boolean.TRUE);categoryList.setRenderer(plainCategory);
        JTextField plate=new JTextField(),make=new JTextField(),model=new JTextField(),seats=new JTextField("5"),rate=new JTextField();
        JPanel form=new JPanel(new GridLayout(0,2,10,10));
        for(JComponent[] pair:new JComponent[][]{{new JLabel("Category"),categoryList},{new JLabel("Registration plate"),plate},{new JLabel("Manufacturer"),make},{new JLabel("Model"),model},{new JLabel("Seats"),seats},{new JLabel("Daily rate (INR)"),rate}}){form.add(pair[0]);form.add(pair[1]);}
        if(JOptionPane.showConfirmDialog(this,form,"Register company vehicle",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try{
            var selected=(VehicleCategoryRecord)categoryList.getSelectedItem();int capacity=Integer.parseInt(seats.getText().strip());
            long daily=new BigDecimal(rate.getText().strip()).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact();
            String registration=plate.getText().strip().toUpperCase(Locale.ROOT),manufacturer=make.getText().strip(),vehicleModel=model.getText().strip();
            if(selected==null||capacity<=0||daily<=0||registration.length()<4||registration.length()>20||manufacturer.isBlank()||manufacturer.length()>80||vehicleModel.isBlank()||vehicleModel.length()>80)throw new IllegalArgumentException();
            long hourly=BigDecimal.valueOf(daily).divide(BigDecimal.valueOf(24),0,RoundingMode.HALF_UP).longValueExact();
            save(()->api.registerVehicle(selected.id(),registration,manufacturer,vehicleModel,capacity,daily,hourly),"Vehicle saved.");
        }catch(RuntimeException ex){feedback.setText("Check the registration, manufacturer, model, positive seat count and rate with at most two decimal places.");}
    }
    private void save(Runnable operation,String success){
        if(!active)return;busy(true);feedback.setText("Saving…");
        pending=new SwingWorker<Void,Void>(){
            protected Void doInBackground(){operation.run();return null;}
            protected void done(){if(!active)return;try{get();feedback.setText(success);reload();}catch(Exception ex){failure(ex);busy(false);}}
        };pending.execute();
    }
    private void failure(Exception failure){
        Throwable cause=failure.getCause()==null?failure:failure.getCause();
        feedback.setText(cause instanceof SecurityException?"Access is no longer authorized. Sign out and sign in again.":"Request did not complete. Refresh records before retrying; no successful save is assumed.");
    }
}
