import java.sql.*;




public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:/Users/nabeelfarooqi/IdeaProjects/Password Manager/passwords.db";
    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(URL);
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS passwords (id INTEGER PRIMARY KEY, site TEXT, username TEXT, password TEXT)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void savePassword(String site, String username, String password){
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO passwords(site, username, password) VALUES(?,?,?)")){
            ps.setString(1, site);
            ps.setString(2, username);
            ps.setString(3, password);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace();}
    }


    public static ResultSet getAllPasswords() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        return conn.createStatement().executeQuery("SELECT site, username, password FROM passwords");
    }























}
