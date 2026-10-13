# 🚀 FUNDORA - Social FinTech Full-Stack Java Prototype

**Fundora** is a production-ready social FinTech web application for college students, flatmates, and youth circles. It unifies daily expense splitting (rent, utility bills, groceries), collaborative group savings for trips/events, transparent shared ledgers, non-intrusive smart reminder nudges, and direct UPI settlements.

This project is built to map to the **Mumbai University Full-Stack Java Programming Syllabus (Modules I to VI)**.

---

## 📚 1. Mumbai University Syllabus Mapping Matrix

| Syllabus Module | Concepts Covered | Corresponding Fundora Source Code Files |
| :--- | :--- | :--- |
| **Module I: OOP Constructs & I/O** | Custom domain classes, encapsulation, method overloading, packages (`com.fundora.*`), stream aggregations. | [`User.java`](src/main/java/com/fundora/model/User.java), [`Group.java`](src/main/java/com/fundora/model/Group.java), [`Expense.java`](src/main/java/com/fundora/model/Expense.java), [`SavingsGoal.java`](src/main/java/com/fundora/model/SavingsGoal.java) |
| **Module II: Inheritance, Interfaces & Exceptions** | Polymorphic interface design (`ExpenseSplitter` (Equal / Exact / Percentage), `PaymentGatewayInterface`), custom exception hierarchy. | [`ExpenseSplitter.java`](src/main/java/com/fundora/service/ExpenseSplitter.java), [`EqualExpenseSplitter.java`](src/main/java/com/fundora/service/EqualExpenseSplitter.java), [`PaymentGatewayInterface.java`](src/main/java/com/fundora/service/PaymentGatewayInterface.java), [`LedgerException.java`](src/main/java/com/fundora/exception/LedgerException.java), [`UserNotFoundException.java`](src/main/java/com/fundora/exception/UserNotFoundException.java), [`LedgerMismatchException.java`](src/main/java/com/fundora/exception/LedgerMismatchException.java) |
| **Module III: Enterprise Web & Database** | Raw JDBC (`DriverManager`, `PreparedStatement`, `ResultSet`), `HttpServlet`, HTTP Session state, `Filter` API (`AuthFilter`), Dynamic JSP + JSTL (`c:forEach`, `c:if`, `<fmt:>`). | [`DatabaseConfig.java`](src/main/java/com/fundora/config/DatabaseConfig.java), [`JdbcLedgerDao.java`](src/main/java/com/fundora/repository/JdbcLedgerDao.java), [`AuthFilter.java`](src/main/java/com/fundora/config/AuthFilter.java), [`ExpenseServlet.java`](src/main/java/com/fundora/controller/ExpenseServlet.java), [`dashboard.jsp`](src/main/webapp/WEB-INF/jsp/dashboard.jsp), [`group-ledger.jsp`](src/main/webapp/WEB-INF/jsp/group-ledger.jsp) |
| **Module IV: JavaScript & DOM** | Client-side DOM manipulation, event listeners, dynamic DOM traversal using `NodeIterator`, asynchronous `Fetch API`. | [`app.js`](src/main/webapp/static/js/app.js) |
| **Module V: MVC Pattern & React.js** | MVC separation, React SPA component with `useState`, `useEffect`, dynamic animated progress bars, live deposit calculations. | [`react-components.js`](src/main/webapp/static/js/react-components.js), [`dashboard.jsp`](src/main/webapp/WEB-INF/jsp/dashboard.jsp) |
| **Module VI: Spring Framework & RESTful APIs** | Spring Boot, IoC / Dependency Injection (`@Autowired`, `@Service`, `@Repository`), REST endpoints (`/api/groups/{id}/ledger`, `/api/expenses/add`, `/api/nudge`, `/api/settle/upi`). | [`FundoraRestController.java`](src/main/java/com/fundora/controller/FundoraRestController.java), [`LedgerService.java`](src/main/java/com/fundora/service/LedgerService.java), [`NudgeService.java`](src/main/java/com/fundora/service/NudgeService.java), [`UPISettlementService.java`](src/main/java/com/fundora/service/UPISettlementService.java), [`FundoraApplication.java`](src/main/java/com/fundora/FundoraApplication.java) |

---

## 🛠️ 2. Project Architecture & Min-Cash-Flow Algorithm

```mermaid
graph TD
    Client[Web Browser / Mobile Client] -->|HTTP / REST| Filter[AuthFilter / Session]
    Filter --> Servlet[ExpenseServlet - JSP MVC]
    Filter --> RestCtrl[FundoraRestController - Spring REST API]
    
    Servlet --> LedgerSvc[LedgerService - Min-Cash-Flow Engine]
    RestCtrl --> LedgerSvc
    RestCtrl --> NudgeSvc[NudgeService - Smart Reminders]
    RestCtrl --> UPISvc[UPISettlementService - PaymentGateway]
    RestCtrl --> SavingsSvc[SavingsGoalService - Trip Goals]
    
    LedgerSvc --> SplitStrategy[ExpenseSplitter Strategy: Equal / Exact]
    LedgerSvc --> JPA[Spring Data JPA Repositories]
    LedgerSvc --> JDBC[JdbcLedgerDao: PreparedStatement & ResultSet]
    
    JPA --> DB[(MySQL / H2 In-Memory DB)]
    JDBC --> DB
```

---

## 🏃 3. How to Run the Application

### Prerequisites:
- Java JDK 17+
- Maven 3.8+

### Step 1: Clone and Open the Project Directory
```powershell
git clone https://github.com/CodesbyRohit/Fundora.git
cd Fundora
```

### Step 2: Build and Run with Maven
```powershell
mvn clean spring-boot:run
```

### Step 3: Open in Browser
- 🌐 **Web Dashboard:** [http://localhost:8080/dashboard](http://localhost:8080/dashboard)
- 🗄️ **H2 SQL Database Console:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:fundoradb`, User: `sa`, Password: empty)
- 📡 **REST API Sample:** [http://localhost:8080/api/groups/1/ledger](http://localhost:8080/api/groups/1/ledger)

### Run the Tests
```powershell
mvn test
```

### Run with Docker
```powershell
docker build -t fundora .
docker run -p 8080:8080 fundora
```
The app is packaged as an executable **WAR** (`target/fundora-fintech.war`) because JSP views cannot be served from a plain executable JAR. It honours the `PORT` environment variable used by Render / Railway / Fly.io.

### API Notes
- State-changing endpoints (`/api/expenses/add`, `/api/nudge`, `/api/settle/upi`) require a signed-in session. For curl/Postman testing, send the header `X-Fundora-User-Id: <userId>` instead.
- You can only settle **your own** debts and nudge **your own** debtors (`403` otherwise). A UPI settlement is rejected with `422` if the payer's wallet balance is too low.
- Errors are returned as JSON: `{"code": "...", "message": "..."}` with a matching 400 / 401 / 403 / 404 / 422 status.
- `POST /api/expenses/add` supports `splitType` `EQUAL`, `EXACT` (with `exactSplits: {"userId": amount}`) and `PERCENTAGE` (with `percentageSplits: {"userId": percent}` adding up to 100).

### Static Demo (GitHub Pages / Vercel)
The `public/` folder is a browser-only demo of the same flows (no backend). Its data is kept in `localStorage`; use **↺ Reset demo data** on the dashboard to start over.
