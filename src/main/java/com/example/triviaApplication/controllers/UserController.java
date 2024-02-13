package com.example.triviaApplication.controllers;
import com.example.triviaApplication.models.Question;
import com.example.triviaApplication.models.Quiz;
import com.example.triviaApplication.models.QuizAttempt;
import com.example.triviaApplication.models.User;
import com.example.triviaApplication.repositories.*;
import com.example.triviaApplication.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationManager;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import com.example.triviaApplication.services.ImageDataService;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "http://localhost:3000")

public class UserController {
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final JavaMailSender javaMailSender;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    ImageRepository imageRepository;

    @Autowired
    private ImageDataService imageDataService;
    @Autowired
    public UserController(UserRepository userRepository, QuestionRepository questionRepository,ImageDataService imageDataService, JavaMailSender javaMailSender) {
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.imageDataService = imageDataService;
        this.javaMailSender = javaMailSender;
    }

    @PutMapping("/upload/image/{id}")
    public ResponseEntity<String> uploadImage(@RequestParam("image") MultipartFile file, @PathVariable Long id) throws IOException {
        String uploadImageResult = imageDataService.uploadImage(file, String.valueOf(id));
        if (uploadImageResult != null) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(uploadImageResult);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload image.");
        }
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> getImage(@PathVariable String id) {
        User user = imageDataService.getImage(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + user.getProfilePicture() + "\"")
                .body(user.getProfilePicture());
    }

    @GetMapping("/{username}/search")
    public ResponseEntity<List<Question>> searchUserQuestionsByKeyword(@PathVariable String username, @RequestParam String keyword) {
        List<Question> questions = questionRepository.searchByUserAndKeyword(username, keyword);
        return ResponseEntity.ok(questions);
    }

    @PostMapping("/register")
    public User createUser(@RequestBody User user) throws Exception {

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new Exception("Username already taken");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new Exception("Email already taken");
        }
        String password = encoder.encode(user.getPassword());
        user.setPassword(password);
        User savedUser = userRepository.save(user);
        User createdUser = ResponseEntity.ok(savedUser).getBody();
        sendRegistrationEmail(createdUser);

        return createdUser;
    }
    @PostMapping("/login")
    public ResponseEntity<String> loginUser(@RequestBody User user) throws Exception {
        // Authenticate the user
        Optional<User> u = userRepository.findByUsername(user.getUsername());

        if (u.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        // Log or debug user properties
        System.out.println("Retrieved User: " + u.get().getIsEmailVerified());
        if(u.isEmpty() || !encoder.matches(user.getPassword(), u.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("message", "Invalid credentials").toString());
        }
        System.out.println("Retrieved User VERIFY STATUS: " + u.get().getIsEmailVerified() + user.getUsername());
        if (u.get().getIsEmailVerified() == 1) {
            //Generate JWT token
            String token = jwtTokenProvider.generateToken(user.getUsername());
            // Return the token in the response
            return ResponseEntity.ok(token);
        }else if(u.get().getIsEmailVerified() == 0){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("message", "Email isn't verified").toString());
        }

        return null;
    }

    @GetMapping("/getUser/{username}")
    public ResponseEntity<User> getUserById(@PathVariable String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/verify-email")
    public ResponseEntity<String> updateIsEmailVerified(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);

        if (user != null) {
            // Update is_email_verified value to 1
            user.setIsEmailVerified(1);
            userRepository.save(user);
            return ResponseEntity.ok("Email verification status updated successfully.");
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    // New endpoint to get questions by username
    @GetMapping("/{username}/questions")
    public ResponseEntity<List<Question>> getQuestionsByUsername(@PathVariable String username) {
        List<Question> questions = questionRepository.findQuestionsByUserUsername(username);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/list")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    private void sendRegistrationEmail(User newuser) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(newuser.getEmail());
        message.setSubject("Welcome to YourApp!");
        message.setText("Thank you for registering with YourApp. We look forward to your participation. please verify your email address using this link. http://localhost:3000/verify-email/" + newuser.getId());

        // Send the email
        javaMailSender.send(message);
    }

    @GetMapping("/profile/info")
    public ResponseEntity<User> getUserProfileInfo(Principal principal) {
        // Fetch the currently logged-in user's profile information
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElse(null);

        if (user != null) {
            // Fetch count of quizzes created by the user
            long numOfQuizzes = quizRepository.countByUserId(user.getId());
            // Set the count of quizzes in the user object
            user.setNumOfQuizzes(numOfQuizzes);

            return new ResponseEntity<>(user, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/profile/quizzes")
    public ResponseEntity<List<Quiz>> getUserProfileQuizzes(Principal principal) {
        // Fetch quizzes created by the currently logged-in user
        String username = principal.getName();
        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            List<Quiz> quizzes = quizRepository.findByUserId(user.getId());
            return new ResponseEntity<>(quizzes, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/profile/attempts")
    public ResponseEntity<List<QuizAttempt>> getUserProfileQuizAttempts(Principal principal) {
        // Fetch quiz attempts made by the currently logged-in user
        String username = principal.getName();
        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            List<QuizAttempt> attempts = quizAttemptRepository.findByUserId(user.getId());
            return new ResponseEntity<>(attempts, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/profile/edit")
    public ResponseEntity<User> editUserProfile(@RequestBody User updatedUser, Principal principal) {
        // Update the profile of the currently logged-in user
        String username = principal.getName();
        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            // Update user information
            user.setUsername(updatedUser.getUsername());
            user.setEmail(updatedUser.getEmail());
            // Update other fields as needed

            // Save the updated user
            User savedUser = userRepository.save(user);
            return new ResponseEntity<>(savedUser, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

}


