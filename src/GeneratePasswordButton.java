import javax.swing.*;
import java.awt.*;


public class GeneratePasswordButton {
    public static void main(String[] args){
        JFrame frame = new JFrame("Password Generator");
        frame.setSize(400,300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JButton button = new JButton("Generate");
        frame.add(button);


        button.addActionListener(e -> {
            String password = PasswordGenerator.generatePassword(12);
            JOptionPane.showMessageDialog(frame, password);
        });


        frame.setLayout(new FlowLayout());

        frame.setVisible(true);





    }




}
