import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * =========================================================================
 * WARNING: ANTI-PATTERN EXAMPLE
 * =========================================================================
 * This is a "God Class" created for testing and demonstration purposes.
 * * It violates the Single Responsibility Principle (SRP) and demonstrates
 * low cohesion and high coupling.
 *
 * DO NOT use this as a model for real software development.
 */
public class GodClass {

    // --- STATE: HR Fields ---
    private Map<String, Employee> employeeDatabase;
    private Map<String, Double> employeeSalaries;
    private Map<String, List<String>> employeeLeaveRequests;
    private Map<String, PerformanceReview> employeeReviews;
    private List<String> openPositions;
    private Map<String, String> onboardingTasks;

    // --- STATE: Finance Fields ---
    private double companyRevenue;
    private double companyExpenses;
    private Map<String, Invoice> pendingInvoices;
    private Map<String, Invoice> paidInvoices;
    private Map<String, String> bankAccountDetails;
    private Map<String, TaxRecord> taxRecords;
    private Map<String, Double> departmentBudgets;

    // --- STATE: Project Management Fields ---
    private Map<String, Project> activeProjects;
    private Map<String, List<Task>> projectTasks;
    private Map<String, String> projectManagers; // Key: ProjectID, Value: EmployeeID
    private Map<String, Double> projectBudgets;
    private Map<String, String> projectTimelines; // Simple string representation

    // --- STATE: IT Fields ---
    private Map<String, String> serverStatus; // Key: ServerIP, Value: "ONLINE" / "OFFLINE"
    private List<HelpdeskTicket> helpdeskTickets;
    private Map<String, SoftwareLicense> softwareLicenses;
    private Map<String, String> userCredentials; // Extremely bad practice!

    // --- STATE: Logistics Fields ---
    private Map<String, Integer> warehouseInventory; // Key: ProductSKU, Value: Quantity
    private Map<String, String> shippingFleetStatus; // Key: TruckID, Value: "IDLE" / "IN_TRANSIT"
    private List<Shipment> pendingShipments;
    private List<Shipment> deliveredShipments;

    /**
     * Constructor initializes all the state for this massive class.
     */
    public GodClass() {
        // Init HR
        this.employeeDatabase = new HashMap<>();
        this.employeeSalaries = new HashMap<>();
        this.employeeLeaveRequests = new HashMap<>();
        this.employeeReviews = new HashMap<>();
        this.openPositions = new ArrayList<>();
        this.onboardingTasks = new HashMap<>();

        // Init Finance
        this.companyRevenue = 0.0;
        this.companyExpenses = 0.0;
        this.pendingInvoices = new HashMap<>();
        this.paidInvoices = new HashMap<>();
        this.bankAccountDetails = new HashMap<>();
        this.taxRecords = new HashMap<>();
        this.departmentBudgets = new HashMap<>();

        // Init Projects
        this.activeProjects = new HashMap<>();
        this.projectTasks = new HashMap<>();
        this.projectManagers = new HashMap<>();
        this.projectBudgets = new HashMap<>();
        this.projectTimelines = new HashMap<>();

        // Init IT
        this.serverStatus = new HashMap<>();
        this.helpdeskTickets = new ArrayList<>();
        this.softwareLicenses = new HashMap<>();
        this.userCredentials = new HashMap<>(); // Bad!

        // Init Logistics
        this.warehouseInventory = new HashMap<>();
        this.shippingFleetStatus = new HashMap<>();
        this.pendingShipments = new ArrayList<>();
        this.deliveredShipments = new ArrayList<>();

        // Seed some data
        seedInitialData();
    }

    private void seedInitialData() {
        System.out.println("Seeding initial data for MegaCorp Manager...");
        this.departmentBudgets.put("HR", 1000000.0);
        this.departmentBudgets.put("Finance", 500000.0);
        this.departmentBudgets.put("IT", 2000000.0);
        this.departmentBudgets.put("Logistics", 1500000.0);

        this.serverStatus.put("10.0.0.1", "ONLINE");
        this.serverStatus.put("10.0.0.2", "ONLINE");
        this.serverStatus.put("10.0.0.3", "OFFLINE");

        this.warehouseInventory.put("SKU-001", 1000);
        this.warehouseInventory.put("SKU-002", 500);

        this.shippingFleetStatus.put("TRUCK-01", "IDLE");
        this.shippingFleetStatus.put("TRUCK-02", "IN_TRANSIT");

        System.out.println("Data seeding complete.");
    }

    // =========================================================================
    // HR METHODS (Responsibility 1)
    // =========================================================================

    public String hireEmployee(String name, String position, String department, double salary) {
        String empId = "E" + (employeeDatabase.size() + 1001);
        Employee emp = new Employee(empId, name, position, department);
        this.employeeDatabase.put(empId, emp);
        this.employeeSalaries.put(empId, salary);
        this.onboardingTasks.put(empId, "Pending: Complete paperwork");
        
        // Directly manipulating IT state (Bad!)
        this.userCredentials.put(name.toLowerCase(), "password123");
        
        System.out.println("HIRE: Hired " + name + " (ID: " + empId + ") for " + position);
        return empId;
    }

    public void fireEmployee(String empId) {
        if (this.employeeDatabase.containsKey(empId)) {
            Employee emp = this.employeeDatabase.remove(empId);
            this.employeeSalaries.remove(empId);
            this.onboardingTasks.remove(empId);
            
            // Directly manipulating IT state (Bad!)
            this.userCredentials.remove(emp.name.toLowerCase());
            
            System.out.println("TERMINATE: Fired " + emp.name + " (ID: " + empId + ")");
        } else {
            System.err.println("TERMINATE: No employee found with ID " + empId);
        }
    }

    public void promoteEmployee(String empId, String newPosition, double newSalary) {
        if (this.employeeDatabase.containsKey(empId)) {
            Employee emp = this.employeeDatabase.get(empId);
            emp.position = newPosition;
            this.employeeSalaries.put(empId, newSalary);
            System.out.println("PROMOTION: " + emp.name + " promoted to " + newPosition);
        } else {
            System.err.println("PROMOTION: Failed. No employee found with ID " + empId);
        }
    }

    public void submitLeaveRequest(String empId, String startDate, String endDate) {
        if (this.employeeDatabase.containsKey(empId)) {
            if (!this.employeeLeaveRequests.containsKey(empId)) {
                this.employeeLeaveRequests.put(empId, new ArrayList<>());
            }
            String request = "PENDING: " + startDate + " to " + endDate;
            this.employeeLeaveRequests.get(empId).add(request);
            System.out.println("LEAVE: Request submitted for " + empId);
        }
    }

    public void approveLeaveRequest(String empId, String startDate) {
        // This is inefficient, but common in God Classes
        if (this.employeeLeaveRequests.containsKey(empId)) {
            List<String> requests = this.employeeLeaveRequests.get(empId);
            for (int i = 0; i < requests.size(); i++) {
                if (requests.get(i).contains(startDate) && requests.get(i).startsWith("PENDING")) {
                    requests.set(i, requests.get(i).replace("PENDING", "APPROVED"));
                    System.out.println("LEAVE: Approved for " + empId);
                    return;
                }
            }
        }
    }
    
    public void rejectLeaveRequest(String empId, String startDate) {
        if (this.employeeLeaveRequests.containsKey(empId)) {
            List<String> requests = this.employeeLeaveRequests.get(empId);
            for (int i = 0; i < requests.size(); i++) {
                if (requests.get(i).contains(startDate) && requests.get(i).startsWith("PENDING")) {
                    requests.set(i, requests.get(i).replace("PENDING", "REJECTED"));
                    System.out.println("LEAVE: Rejected for " + empId);
                    return;
                }
            }
        }
    }

    public Employee getEmployeeDetails(String empId) {
        return this.employeeDatabase.get(empId);
    }

    public Double getEmployeeSalary(String empId) {
        return this.employeeSalaries.get(empId);
    }

    public List<String> getEmployeeLeaveHistory(String empId) {
        return this.employeeLeaveRequests.getOrDefault(empId, new ArrayList<>());
    }
    
    public List<Employee> listAllEmployees() {
        return new ArrayList<>(this.employeeDatabase.values());
    }

    public void postNewJobOpening(String positionTitle) {
        this.openPositions.add(positionTitle);
        System.out.println("HR: New job opening posted: " + positionTitle);
    }

    public List<String> getOpenPositions() {
        return this.openPositions;
    }

    public String getOnboardingTaskStatus(String empId) {
        return this.onboardingTasks.getOrDefault(empId, "N/A");
    }

    public void completeOnboardingTask(String empId) {
        if(this.onboardingTasks.containsKey(empId)) {
            this.onboardingTasks.put(empId, "Completed");
            System.out.println("HR: Onboarding complete for " + empId);
        }
    }

    // ... More HR methods (e.g., submitPerformanceReview, getPerformanceReview, etc.)
    // ... (Adding 50 more would be typical)

    // =========================================================================
    // FINANCE METHODS (Responsibility 2)
    // =========================================================================

    public String createInvoice(String clientName, double amount) {
        String invId = "INV-" + (pendingInvoices.size() + paidInvoices.size() + 100);
        Invoice inv = new Invoice(invId, clientName, amount, "PENDING");
        this.pendingInvoices.put(invId, inv);
        this.companyRevenue += amount; // Assuming revenue is recognized on invoice creation (may be wrong)
        
        System.out.println("FINANCE: Created invoice " + invId + " for $" + amount);
        return invId;
    }

    public void payInvoice(String invId) {
        if (this.pendingInvoices.containsKey(invId)) {
            Invoice inv = this.pendingInvoices.remove(invId);
            inv.status = "PAID";
            this.paidInvoices.put(invId, inv);
            System.out.println("FINANCE: Invoice " + invId + " marked as PAID.");
        } else {
            System.err.println("FINANCE: Could not find pending invoice " + invId);
        }
    }

    public void recordExpense(String description, String department, double amount) {
        this.companyExpenses += amount;
        if(this.departmentBudgets.containsKey(department)) {
            double budget = this.departmentBudgets.get(department);
            this.departmentBudgets.put(department, budget - amount);
            System.out.println("FINANCE: Recorded expense of $" + amount + " for " + department);
        } else {
            System.err.println("FINANCE: No budget found for department " + department);
        }
    }

    public double getNetIncome() {
        return this.companyRevenue - this.companyExpenses;
    }

    public double getGrossRevenue() {
        return this.companyRevenue;
    }
    
    public double getGrossExpenses() {
        return this.companyExpenses;
    }

    public double getDepartmentBudget(String department) {
        return this.departmentBudgets.getOrDefault(department, 0.0);
    }
    
    public boolean isDepartmentOverBudget(String department) {
        return this.departmentBudgets.getOrDefault(department, 0.0) < 0;
    }

    public void processMonthlyPayroll() {
        double totalPayroll = 0;
        for (Map.Entry<String, Double> entry : this.employeeSalaries.entrySet()) {
            double monthlySalary = entry.getValue() / 12;
            totalPayroll += monthlySalary;
            
            // Logic to connect to a bank API...
            System.out.println("PAYROLL: Processing $" + monthlySalary + " for " + entry.getKey());
        }
        this.recordExpense("Monthly Payroll", "Corporate", totalPayroll);
        System.out.println("PAYROLL: Monthly payroll processed. Total: $" + totalPayroll);
    }
    
    public List<Invoice> getPendingInvoices() {
        return new ArrayList<>(this.pendingInvoices.values());
    }

    public List<Invoice> getPaidInvoices() {
        return new ArrayList<>(this.paidInvoices.values());
    }
    
    public void addTaxRecord(String recordName, double amount) {
        String taxId = "TAX-" + (this.taxRecords.size() + 1);
        this.taxRecords.put(taxId, new TaxRecord(taxId, recordName, amount));
        System.out.println("FINANCE: Added tax record " + taxId);
    }

    public TaxRecord getTaxRecord(String taxId) {
        return this.taxRecords.get(taxId);
    }
    
    // ... More Finance methods (e.g., generateQuarterlyReport, auditLogs, etc.)
    // ... (Adding 50 more would be typical)

    // =========================================================================
    // PROJECT MANAGEMENT METHODS (Responsibility 3)
    // =========================================================================

    public String createNewProject(String projectName, String managerEmpId, double budget) {
        if (!this.employeeDatabase.containsKey(managerEmpId)) {
            System.err.println("PROJECT: Cannot create project. Manager " + managerEmpId + " not found.");
            return null;
        }
        
        String projId = "PROJ-" + (this.activeProjects.size() + 1);
        Project proj = new Project(projId, projectName, managerEmpId);
        this.activeProjects.put(projId, proj);
        this.projectManagers.put(projId, managerEmpId);
        this.projectBudgets.put(projId, budget);
        this.projectTasks.put(projId, new ArrayList<>());
        this.projectTimelines.put(projId, "Start Date: " + System.currentTimeMillis());
        
        System.out.println("PROJECT: Created new project '" + projectName + "' (ID: " + projId + ")");
        return projId;
    }

    public String addTaskToProject(String projId, String taskName, String assignedToEmpId) {
        if (!this.activeProjects.containsKey(projId)) {
            System.err.println("PROJECT: No project found with ID " + projId);
            return null;
        }
        if (!this.employeeDatabase.containsKey(assignedToEmpId)) {
            System.err.println("PROJECT: Cannot assign task. Employee " + assignedToEmpId + " not found.");
            return null;
        }
        
        String taskId = "T-" + UUID.randomUUID().toString().substring(0, 8);
        Task task = new Task(taskId, taskName, assignedToEmpId, "PENDING");
        this.projectTasks.get(projId).add(task);
        
        System.out.println("PROJECT: Added task '" + taskName + "' to project " + projId);
        return taskId;
    }
    
    public void markTaskComplete(String projId, String taskId) {
        if (this.projectTasks.containsKey(projId)) {
            for (Task task : this.projectTasks.get(projId)) {
                if (task.id.equals(taskId)) {
                    task.status = "COMPLETED";
                    System.out.println("PROJECT: Task " + taskId + " marked COMPLETED.");
                    return;
                }
            }
        }
        System.err.println("PROJECT: Could not find task " + taskId + " in project " + projId);
    }

    public List<Task> getTasksForProject(String projId) {
        return this.projectTasks.getOrDefault(projId, new ArrayList<>());
    }
    
    public String getProjectStatus(String projId) {
        if (!this.projectTasks.containsKey(projId)) {
            return "PROJECT: Not found";
        }
        List<Task> tasks = this.projectTasks.get(projId);
        long completed = tasks.stream().filter(t -> t.status.equals("COMPLETED")).count();
        return "PROJECT: " + projId + " is " + (completed * 100 / (double)tasks.size()) + "% complete.";
    }

    public double getProjectBudgetRemaining(String projId) {
        // This is a stub. A real god class would have complex, tangled logic here
        // to check finance expenses.
        return this.projectBudgets.getOrDefault(projId, 0.0); 
    }
    
    // ... More Project methods (e.g., addMilestone, getTimeline, listAllProjects, etc.)
    // ... (Adding 50 more would be typical)

    // =========================================================================
    // IT METHODS (Responsibility 4)
    // =========================================================================

    public String submitHelpdeskTicket(String empId, String issueDescription) {
        if (!this.employeeDatabase.containsKey(empId)) {
            System.err.println("IT: Cannot create ticket. Employee " + empId + " not found.");
            return null;
        }
        String ticketId = "TKT-" + (this.helpdeskTickets.size() + 1);
        HelpdeskTicket ticket = new HelpdeskTicket(ticketId, empId, issueDescription, "OPEN");
        this.helpdeskTickets.add(ticket);
        System.out.println("IT: New ticket " + ticketId + " created by " + empId);
        return ticketId;
    }

    public void resolveHelpdeskTicket(String ticketId) {
        for (HelpdeskTicket ticket : this.helpdeskTickets) {
            if (ticket.id.equals(ticketId)) {
                ticket.status = "RESOLVED";
                System.out.println("IT: Ticket " + ticketId + " marked RESOLVED.");
                return;
            }
        }
    }
    
    public void closeHelpdeskTicket(String ticketId) {
        for (HelpdeskTicket ticket : this.helpdeskTickets) {
            if (ticket.id.equals(ticketId) && ticket.status.equals("RESOLVED")) {
                ticket.status = "CLOSED";
                System.out.println("IT: Ticket " + ticketId + " CLOSED.");
                return;
            }
        }
    }
    
    public List<HelpdeskTicket> getOpenHelpdeskTickets() {
        List<HelpdeskTicket> openTickets = new ArrayList<>();
        for (HelpdeskTicket ticket : this.helpdeskTickets) {
            if (ticket.status.equals("OPEN")) {
                openTickets.add(ticket);
            }
        }
        return openTickets;
    }

    public String checkServerStatus(String serverIp) {
        return this.serverStatus.getOrDefault(serverIp, "UNKNOWN");
    }

    public void rebootServer(String serverIp) {
        // Horrible place for this logic!
        if (this.serverStatus.containsKey(serverIp)) {
            System.out.println("IT: Attempting to reboot server " + serverIp + "...");
            this.serverStatus.put(serverIp, "REBOOTING");
            // ... logic to ssh and reboot ...
            System.out.println("IT: Server " + serverIp + " reboot command sent.");
            this.serverStatus.put(serverIp, "ONLINE"); // Simulating
        }
    }

    public void assignLicense(String empId, String softwareName) {
        if (!this.employeeDatabase.containsKey(empId)) return;
        String licenseKey = softwareName + "-" + UUID.randomUUID().toString();
        SoftwareLicense license = new SoftwareLicense(licenseKey, softwareName, empId);
        this.softwareLicenses.put(licenseKey, license);
        System.out.println("IT: Assigned " + softwareName + " license to " + empId);
    }
    
    public boolean validateUserCredentials(String username, String password) {
        // This is a massive security vulnerability
        String storedPass = this.userCredentials.get(username);
        return storedPass != null && storedPass.equals(password);
    }
    
    // ... More IT methods (e.g., resetPassword, listAllLicenses, etc.)
    // ... (Adding 50 more would be typical)

    // =========================================================================
    // LOGISTICS METHODS (Responsibility 5)
    // =========================================================================

    public int getStockLevel(String sku) {
        return this.warehouseInventory.getOrDefault(sku, 0);
    }

    public void receiveInventory(String sku, int quantity) {
        int currentStock = this.warehouseInventory.getOrDefault(sku, 0);
        this.warehouseInventory.put(sku, currentStock + quantity);
        System.out.println("LOGISTICS: Received " + quantity + " units of " + sku);
    }

    public String dispatchShipment(String sku, int quantity, String destinationAddress) {
        int currentStock = getStockLevel(sku);
        if (currentStock < quantity) {
            System.err.println("LOGISTICS: Not enough stock for " + sku);
            return null;
        }
        
        // Find an idle truck
        String truckId = null;
        for (Map.Entry<String, String> entry : this.shippingFleetStatus.entrySet()) {
            if (entry.getValue().equals("IDLE")) {
                truckId = entry.getKey();
                break;
            }
        }
        
        if (truckId == null) {
            System.err.println("LOGISTICS: No idle trucks available for shipment.");
            return null;
        }

        this.warehouseInventory.put(sku, currentStock - quantity);
        this.shippingFleetStatus.put(truckId, "IN_TRANSIT");
        
        String shipmentId = "SHIP-" + (this.pendingShipments.size() + 1);
        Shipment shipment = new Shipment(shipmentId, sku, quantity, destinationAddress, truckId);
        this.pendingShipments.add(shipment);
        
        System.out.println("LOGISTICS: Dispatched shipment " + shipmentId + " on " + truckId);
        return shipmentId;
    }
    
    public void markShipmentDelivered(String shipmentId) {
        Shipment found = null;
        for (Shipment s : this.pendingShipments) {
            if (s.id.equals(shipmentId)) {
                found = s;
                break;
            }
        }
        
        if(found != null) {
            this.pendingShipments.remove(found);
            this.deliveredShipments.add(found);
            this.shippingFleetStatus.put(found.truckId, "IDLE"); // Truck is free
            System.out.println("LOGISTICS: Shipment " + shipmentId + " marked DELIVERED.");
        }
    }
    
    public String getTruckStatus(String truckId) {
        return this.shippingFleetStatus.getOrDefault(truckId, "UNKNOWN_TRUCK");
    }

    public List<Shipment> getPendingShipments() {
        return this.pendingShipments;
    }
    
    public Map<String, Integer> getFullInventoryReport() {
        return this.warehouseInventory;
    }

    // ... More Logistics methods (e.g., trackShipment, recallShipment, etc.)
    // ... (Adding 50 more would be typical)


    // =========================================================================
    // NESTED HELPER CLASSES
    // (Often dumped in the same file in a God Class)
    // =========================================================================

    static class Employee {
        String id; String name; String position; String department;
        Employee(String id, String name, String position, String department) {
            this.id = id; this.name = name; this.position = position; this.department = department;
        }
    }

    static class Invoice {
        String id; String client; double amount; String status;
        Invoice(String id, String client, double amount, String status) {
            this.id = id; this.client = client; this.amount = amount; this.status = status;
        }
    }

    static class Project {
        String id; String name; String managerId;
        Project(String id, String name, String managerId) {
            this.id = id; this.name = name; this.managerId = managerId;
        }
    }

    static class Task {
        String id; String name; String assignedToEmpId; String status;
        Task(String id, String name, String assignedToEmpId, String status) {
            this.id = id; this.name = name; this.assignedToEmpId = assignedToEmpId; this.status = status;
        }
    }

    static class HelpdeskTicket {
        String id; String submittedByEmpId; String issue; String status;
        HelpdeskTicket(String id, String submittedByEmpId, String issue, String status) {
            this.id = id; this.submittedByEmpId = submittedByEmpId; this.issue = issue; this.status = status;
        }
    }

    static class SoftwareLicense {
        String key; String softwareName; String assignedToEmpId;
        SoftwareLicense(String key, String softwareName, String assignedToEmpId) {
            this.key = key; this.softwareName = softwareName; this.assignedToEmpId = assignedToEmpId;
        }
    }
    
    static class Shipment {
        String id; String sku; int quantity; String destination; String truckId;
        Shipment(String id, String sku, int quantity, String destination, String truckId) {
            this.id = id; this.sku = sku; this.quantity = quantity; this.destination = destination; this.truckId = truckId;
        }
    }
    
    static class PerformanceReview {
        String empId; int rating; String managerNotes;
        PerformanceReview(String empId, int rating, String managerNotes) {
            this.empId = empId; this.rating = rating; this.managerNotes = managerNotes;
        }
    }
    
    static class TaxRecord {
        String id; String name; double amount;
        TaxRecord(String id, String name, double amount) {
            this.id = id; this.name = name; this.amount = amount;
        }
    }
}