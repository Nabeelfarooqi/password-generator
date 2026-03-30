import java.security.SecureRandom;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class PasswordGenerator {
    private static final String ALL_Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$()";

    public static String generatePassword(int length) {
        if (length < 4) throw new IllegalArgumentException("Length must be at least 4 chaaracters");
        SecureRandom random = new SecureRandom();
        List<Character> pass = new ArrayList<>();

        pass.add("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(random.nextInt(26)));
        pass.add("abcdefghijklmnopqrstuvwxyz".charAt(random.nextInt(26)));
        pass.add("0123456789".charAt(random.nextInt(10)));
        pass.add("!@#$()".charAt(random.nextInt(6)));

        for (int i =4; i < length; i++) pass.add(ALL_Chars.charAt(random.nextInt(ALL_Chars.length())));

        Collections.shuffle(pass);

        StringBuilder sb = new StringBuilder();
        for(char c : pass) sb.append(c);
        return sb.toString();

    }
    public static void main(String[] args){
        System.out.println("Password: " + generatePassword(12));

    }
}

