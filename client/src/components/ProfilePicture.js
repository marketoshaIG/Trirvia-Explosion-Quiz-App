import React, { useState, useEffect } from 'react';
import axios from 'axios';
import apiClient from "./ApiClient";

const ProfilePicture = () => {
    // State variables
    const [file, setFile] = useState(null);
    const [error, setError] = useState('');
    const [uploading, setUploading] = useState(false);
    const [success, setSuccess] = useState('');
    const [userData, setUserData] = useState(null);
    const [loading, setLoading] = useState(true);

    // Fetch user data on component mount
    useEffect(() => {
        const fetchUserData = async () => {
            try {
                const response = await apiClient.get('http://localhost:8080/users/profile/info', {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
                    },
                });
                setUserData(response.data);
                setLoading(false);
            } catch (error) {
                setError('Error fetching user data');
                setLoading(false);
            }
        };

        fetchUserData();
    }, []);

    // Handle file change
    const handleFileChange = (e) => {
        setFile(e.target.files[0]);
    };

    // Handle file upload
    const handleUpload = async () => {
        try {
            setError('');
            setUploading(true);
            setSuccess('');

            const formData = new FormData();
            formData.append('image', file);

            await apiClient.put(`http://localhost:8080/users/upload/image/${userData.id}`, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
            });

            setSuccess('Profile picture uploaded successfully!');
        } catch (error) {
            console.error('Error uploading profile picture:', error);
            setError('Error uploading profile picture. Please try again.');
        } finally {
            setUploading(false);
        }
    };

    // Render loading state
    if (loading) {
        return <div>Loading...</div>;
    }

    // Render error state
    if (error) {
        return <div>{error}</div>;
    }

    return (
        <div className="container mt-5">
            <h1>My Profile</h1>
            {/* Display user information */}
            <div>
                <p>Username: {userData.username}</p>
                <p>Email: {userData.email}</p>
                <p>Number of Quizzes Created: {userData.numOfQuizzes}</p>

            </div>
            {/* Display quizzes created and quiz attempts */}
            <div>
                {/* Display quizzes created by the user */}
                <h2>Quizzes Created</h2>
                {userData.quizzes && userData.quizzes.length > 0 ? (
                    <ul>
                        {userData.quizzes.map((quiz) => (
                            <li key={quiz.id}>{quiz.title}</li>
                        ))}
                    </ul>
                ) : (
                    <p>No quizzes created</p>
                )}
                {/* Display quiz attempts made by the user */}
                <h2>Quiz Attempts</h2>
                {userData.quizAttempts && userData.quizAttempts.length > 0 ? (
                    <ul>
                        {userData.quizAttempts.map((attempt) => (
                            <li key={attempt.id}>{attempt.quizTitle}</li>
                        ))}
                    </ul>
                ) : (
                    <p>No quiz attempts</p>
                )}
            </div>
            {/* Profile picture upload */}
            <div>
                <h2>Upload Profile Picture</h2>
                {error && <div className="alert alert-danger">{error}</div>}
                {uploading && <div className="alert alert-info">Uploading...</div>}
                {success && <div className="alert alert-success">{success}</div>}
                <input type="file" onChange={handleFileChange} />
                <button type="button" onClick={handleUpload} className="btn btn-primary mt-3">
                    Upload
                </button>
            </div>
        </div>
    );
};

export default ProfilePicture;
