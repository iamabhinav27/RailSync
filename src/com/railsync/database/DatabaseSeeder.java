package com.railsync.database;

import com.railsync.manager.ReservationManager;
import com.railsync.model.*;
import com.railsync.repository.StationRepository;
import com.railsync.repository.TrainRepository;
import com.railsync.repository.UserRepository;
import com.railsync.util.PasswordHasher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty database with illustrative reference data (stations, trains, demo accounts and a few
 * bookings). These are sample records, not live railway data. Each part runs only when its table is empty,
 * so restarting the app never duplicates data or overwrites what users have created.
 */
public final class DatabaseSeeder {

    private DatabaseSeeder() {}

    public static void seedReferenceData(StationRepository stations, TrainRepository trains, UserRepository users) {
        if (stations.count() == 0) seedStations(stations);
        if (trains.count() == 0) seedTrains(stations, trains);
        if (users.count() == 0) seedUsers(users);
    }

    static void seedStations(StationRepository repo) {
        Object[][] data = {
            {"NDLS", "New Delhi", "New Delhi", "Delhi", "NR", 0.0},
            {"BCT", "Mumbai Central", "Mumbai", "Maharashtra", "WR", 1384.0},
            {"HWH", "Howrah Junction", "Kolkata", "West Bengal", "ER", 1447.0},
            {"MAS", "Chennai Central", "Chennai", "Tamil Nadu", "SR", 2180.0},
            {"SBC", "KSR Bengaluru", "Bengaluru", "Karnataka", "SWR", 2400.0},
            {"BPL", "Bhopal Junction", "Bhopal", "Madhya Pradesh", "WCR", 705.0},
            {"CNB", "Kanpur Central", "Kanpur", "Uttar Pradesh", "NCR", 440.0},
            {"PNBE", "Patna Junction", "Patna", "Bihar", "ECR", 998.0},
            {"ADI", "Ahmedabad Junction", "Ahmedabad", "Gujarat", "WR", 934.0},
            {"PUNE", "Pune Junction", "Pune", "Maharashtra", "CR", 1520.0},
            {"NGP", "Nagpur Junction", "Nagpur", "Maharashtra", "CR", 1090.0},
            {"JAT", "Jammu Tawi", "Jammu", "Jammu and Kashmir", "NR", 580.0},
            {"TVC", "Thiruvananthapuram Central", "Thiruvananthapuram", "Kerala", "SR", 3050.0},
            {"GHY", "Guwahati Junction", "Guwahati", "Assam", "NFR", 1920.0},
            {"BSB", "Varanasi Junction", "Varanasi", "Uttar Pradesh", "NER", 780.0},
            {"AGC", "Agra Cantt", "Agra", "Uttar Pradesh", "NCR", 195.0},
            {"CDG", "Chandigarh Junction", "Chandigarh", "Chandigarh", "NR", 245.0},
        };
        for (Object[] d : data) {
            repo.save(new Station((String) d[0], (String) d[1], (String) d[2], (String) d[3], (String) d[4], (Double) d[5]));
        }
    }

    static void seedTrains(StationRepository stationRepo, TrainRepository repo) {
        Station ndls = stationRepo.findById("NDLS"), bct = stationRepo.findById("BCT"), hwh = stationRepo.findById("HWH"),
                mas = stationRepo.findById("MAS"), sbc = stationRepo.findById("SBC"), bpl = stationRepo.findById("BPL"),
                cnb = stationRepo.findById("CNB"), bsb = stationRepo.findById("BSB"), adi = stationRepo.findById("ADI"),
                tvc = stationRepo.findById("TVC"), ngp = stationRepo.findById("NGP"), agc = stationRepo.findById("AGC"),
                cdg = stationRepo.findById("CDG");

        RajdhaniExpress t1 = new RajdhaniExpress("12952", "Mumbai Rajdhani", ndls, bct, LocalTime.of(16, 55), LocalTime.of(8, 35), 1384.0);
        t1.addIntermediateStation(bpl);
        t1.addCoach(new Coach("H1", SeatClass.FIRST_AC, 18, 4, 10));
        t1.addCoach(new Coach("A1", SeatClass.SECOND_AC, 36, 6, 15));
        t1.addCoach(new Coach("A2", SeatClass.SECOND_AC, 36, 6, 15));
        t1.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
        t1.addCoach(new Coach("B2", SeatClass.THIRD_AC, 48, 8, 20));
        repo.save(t1);

        RajdhaniExpress t2 = new RajdhaniExpress("12951", "NDLS Rajdhani Express", bct, ndls, LocalTime.of(17, 0), LocalTime.of(8, 32), 1384.0);
        t2.addIntermediateStation(bpl);
        t2.addCoach(new Coach("H1", SeatClass.FIRST_AC, 18, 4, 10));
        t2.addCoach(new Coach("A1", SeatClass.SECOND_AC, 36, 6, 15));
        t2.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
        repo.save(t2);

        VandeBharatExpress t3 = new VandeBharatExpress("22436", "Vande Bharat Express", ndls, bsb, LocalTime.of(6, 0), LocalTime.of(14, 0), 780.0);
        t3.addIntermediateStation(cnb);
        t3.addCoach(new Coach("C1", SeatClass.CHAIR_CAR, 50, 10, 20));
        t3.addCoach(new Coach("C2", SeatClass.CHAIR_CAR, 50, 10, 20));
        t3.addCoach(new Coach("E1", SeatClass.FIRST_AC, 24, 4, 8));
        repo.save(t3);

        ShatabdiExpress t4 = new ShatabdiExpress("12002", "Bhopal Shatabdi", ndls, bpl, LocalTime.of(6, 15), LocalTime.of(14, 40), 705.0);
        t4.addIntermediateStation(agc);
        t4.addCoach(new Coach("C1", SeatClass.CHAIR_CAR, 45, 8, 15));
        t4.addCoach(new Coach("C2", SeatClass.CHAIR_CAR, 45, 8, 15));
        t4.addCoach(new Coach("E1", SeatClass.FIRST_AC, 20, 4, 10));
        repo.save(t4);

        RajdhaniExpress t5 = new RajdhaniExpress("12301", "Howrah Rajdhani", hwh, ndls, LocalTime.of(16, 50), LocalTime.of(10, 5), 1447.0);
        t5.addIntermediateStation(cnb);
        t5.addCoach(new Coach("H1", SeatClass.FIRST_AC, 18, 4, 10));
        t5.addCoach(new Coach("A1", SeatClass.SECOND_AC, 36, 6, 15));
        t5.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
        repo.save(t5);

        SuperfastExpress t6 = new SuperfastExpress("12626", "Kerala Express", ndls, tvc, LocalTime.of(20, 10), LocalTime.of(18, 0), 3050.0);
        t6.addIntermediateStation(bpl);
        t6.addIntermediateStation(ngp);
        t6.addIntermediateStation(mas);
        t6.addCoach(new Coach("S1", SeatClass.SLEEPER, 60, 12, 30));
        t6.addCoach(new Coach("S2", SeatClass.SLEEPER, 60, 12, 30));
        t6.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
        t6.addCoach(new Coach("A1", SeatClass.SECOND_AC, 36, 6, 15));
        repo.save(t6);

        ExpressTrain t7 = new ExpressTrain("12137", "Punjab Mail", bct, ndls, LocalTime.of(19, 35), LocalTime.of(21, 30), 1540.0);
        t7.addIntermediateStation(bpl);
        t7.addCoach(new Coach("S1", SeatClass.SLEEPER, 60, 12, 30));
        t7.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
        repo.save(t7);

        SuperfastExpress t8 = new SuperfastExpress("12628", "Karnataka Express", ndls, sbc, LocalTime.of(21, 15), LocalTime.of(12, 0), 2400.0);
        t8.addIntermediateStation(bpl);
        t8.addCoach(new Coach("S1", SeatClass.SLEEPER, 60, 12, 30));
        t8.addCoach(new Coach("B1", SeatClass.THIRD_AC, 48, 8, 20));
        t8.addCoach(new Coach("A1", SeatClass.SECOND_AC, 36, 6, 15));
        repo.save(t8);

        ExpressTrain t9 = new ExpressTrain("12933", "Karnavati Express", bct, adi, LocalTime.of(14, 5), LocalTime.of(21, 5), 492.0);
        t9.addCoach(new Coach("C1", SeatClass.CHAIR_CAR, 50, 10, 20));
        t9.addCoach(new Coach("C2", SeatClass.CHAIR_CAR, 50, 10, 20));
        repo.save(t9);

        ShatabdiExpress t10 = new ShatabdiExpress("12011", "Kalka Shatabdi", ndls, cdg, LocalTime.of(7, 40), LocalTime.of(11, 45), 245.0);
        t10.addCoach(new Coach("C1", SeatClass.CHAIR_CAR, 50, 8, 15));
        t10.addCoach(new Coach("E1", SeatClass.FIRST_AC, 20, 4, 10));
        repo.save(t10);
    }

    static void seedUsers(UserRepository users) {
        // Demo accounts. Passwords are stored as salted PBKDF2 hashes, never in plaintext.
        users.save(new PassengerAccount("0", "passenger", PasswordHasher.hash("pass123"), "Rahul Sharma",
                "rahul.sharma@example.com", "9876543210"));
        users.save(new PassengerAccount("0", "priya", PasswordHasher.hash("pass123"), "Priya Patel",
                "priya.patel@example.com", "9823456781"));
        users.save(new Admin("0", "admin", PasswordHasher.hash("admin123"), "Chief Commercial Officer",
                "admin@railsync.gov.in", "9999900000"));
    }

    /** A few bookings (CONFIRMED) so the history and admin screens are not empty on first run. */
    public static void seedSampleBookings(ReservationManager manager, long existingBookings) {
        if (existingBookings > 0) return;
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        try {
            List<Passenger> p1 = new ArrayList<>();
            p1.add(new Passenger("Rahul Sharma", 32, "Male", "9876543210", BerthType.LOWER));
            p1.add(new Passenger("Sunita Sharma", 62, "Female", "9876543210", BerthType.LOWER));
            manager.bookTicket("12952", tomorrow, SeatClass.SECOND_AC, p1, "passenger");

            List<Passenger> p2 = new ArrayList<>();
            p2.add(new Passenger("Priya Patel", 28, "Female", "9823456781", BerthType.WINDOW));
            manager.bookTicket("22436", tomorrow, SeatClass.CHAIR_CAR, p2, "priya");

            List<Passenger> p3 = new ArrayList<>();
            p3.add(new Passenger("Amitabh Verma", 45, "Male", "9123456789", BerthType.UPPER));
            p3.add(new Passenger("Aarav Verma", 8, "Male", "9123456789", BerthType.MIDDLE));
            manager.bookTicket("12626", nextWeek, SeatClass.SLEEPER, p3, "passenger");
        } catch (Exception e) {
            System.err.println("Notice during sample bookings: " + e.getMessage());
        }
    }
}
